package com.feetfit.server.service.ShoeService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.repository.*;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:recommendation-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.connection-init-sql=",
        "jwt.secret=ZmFrZS10ZXN0LWp3dC1zZWNyZXQtZmFrZS10ZXN0LWp3dC1zZWNyZXQ=",
        "INTERNAL_API_KEY=test-internal-key"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShoeRecommendationConcurrencyJpaIntegrationTest {

    @Autowired ShoeCommandService shoeCommandService;
    @Autowired ShoeRecommendationRunService runService;
    @Autowired UserRepository userRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired MeasurementSessionRepository measurementSessionRepository;
    @Autowired ShoeRepository shoeRepository;
    @Autowired ShoeRecommendationRepository recommendationRepository;
    @Autowired ShoeRecommendationReasonRepository reasonRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void concurrentSameSessionAndShoeProducesOneRecommendationWithThreeReasons() throws Exception {
        Fixture fixture = transactionTemplate.execute(ignored -> {
            Device device = deviceRepository.save(Device.builder().deviceName("concurrent-device").build());
            User user = userRepository.save(User.builder()
                    .nickname("concurrent-user")
                    .socialType(SocialType.KAKAO)
                    .socialId("concurrent-social")
                    .build());
            Shoe shoe = shoeRepository.save(Shoe.builder()
                    .brandName("brand")
                    .shoeName("shoe")
                    .modelCode("concurrent-model")
                    .musinsaGoodsNo("concurrent-goods")
                    .musinsaUrl("https://example.com/concurrent-shoe")
                    .build());
            MeasurementSession session = measurementSessionRepository.save(MeasurementSession.builder()
                    .user(user)
                    .device(device)
                    .status(MeasurementStatus.COMPLETED)
                    .measuredAt(LocalDateTime.now())
                    .build());
            return new Fixture(user.getId(), shoe.getId(), session.getId());
        });
        runService.startRun(fixture.userId(), fixture.sessionId(), 1, false);
        ShoeRequestDTO.SaveShoeRecommendationDTO request = request(fixture);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> first = executor.submit(() -> invokeAfter(start, fixture.userId(), request));
            Future<?> second = executor.submit(() -> invokeAfter(start, fixture.userId(), request));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(recommendationRepository.countByMeasurementSessionId(fixture.sessionId())).isOne();
        ShoeRecommendation recommendation = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(fixture.sessionId(), fixture.shoeId())
                .orElseThrow();
        List<ShoeRecommendationReason> reasons = reasonRepository
                .findByShoeRecommendationId(recommendation.getId());
        assertThat(reasons).hasSize(3);
        assertThat(reasons).extracting(ShoeRecommendationReason::getReasonType)
                .doesNotHaveDuplicates();
    }

    private void invokeAfter(
            CountDownLatch start, Long userId, ShoeRequestDTO.SaveShoeRecommendationDTO request) {
        try {
            start.await();
            shoeCommandService.saveShoeRecommendations(userId, request);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static ShoeRequestDTO.SaveShoeRecommendationDTO request(Fixture fixture) throws Exception {
        return new ObjectMapper().readValue("""
                {"measurementSessionId":%d,"recommendations":[{
                  "shoeId":%d,"fitScore":88,"pointSummary":"summary","reasons":[
                    {"reasonType":"FOREFOOT","title":"forefoot","riskLevel":"LOW","reviewIds":[]},
                    {"reasonType":"HEEL","title":"heel","riskLevel":"MEDIUM","reviewIds":[]},
                    {"reasonType":"INSOLE","title":"insole","riskLevel":"HIGH","reviewIds":[]}
                  ]
                }]}
                """.formatted(fixture.sessionId(), fixture.shoeId()),
                ShoeRequestDTO.SaveShoeRecommendationDTO.class);
    }

    private record Fixture(Long userId, Long shoeId, Long sessionId) {
    }
}
