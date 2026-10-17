package com.feetfit.server.service.DrugService;

import com.feetfit.server.config.AsyncConfig;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.*;
import com.feetfit.server.event.MeasurementCompletedEvent;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({JacksonAutoConfiguration.class, AsyncConfig.class, DrugCatalogMapper.class,
        DrugRecommendationPolicy.class, DrugRecommendationStore.class, DrugRecommendationQueryService.class,
        DrugRecommendationGenerationService.class, DrugRecommendationListener.class,
        DrugRecommendationSocketNotifier.class, MeasurementSocketService.class})
class DrugRecommendationCompletionJpaIntegrationTest {
    @Autowired TestEntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationEventPublisher events;
    @Autowired MeasurementSocketService socket;
    @Autowired DrugRecommendationQueryService query;
    @Autowired @Qualifier("drugRecommendationTaskExecutor") Executor recommendationExecutor;
    @MockBean DrugRecommendationSummaryClient client;
    @MockBean SimpMessagingTemplate messaging;
    private TransactionTemplate transactions;

    @BeforeEach
    void setup() {
        transactions = new TransactionTemplate(transactionManager);
        when(client.model()).thenReturn(DrugRecommendationSummaryClient.DEFAULT_MODEL);
    }

    @Test
    void frontendCompletionWaitsForGptAndCommittedSummaryIsReturnedWithList() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.generate(any())).thenAnswer(call -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return "Persisted GPT explanation";
        });
        Fixture fixture;
        try {
            fixture = completeReport();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var pending = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
            verify(messaging).convertAndSend(eq(fixture.topic()), pending.capture());
            assertThat(pending.getValue().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
            assertThat(pending.getValue().getShouldDisconnect()).isFalse();
            assertThat(query.get(fixture.userId(), null, null, 0, 20).getSummaryStatus()).isEqualTo("GENERATING");
        } finally {
            release.countDown();
        }
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(messaging, timeout(5000).times(2)).convertAndSend(eq(fixture.topic()), messages.capture());
        assertThat(messages.getAllValues()).extracting(MeasurementResponseDTO.MeasurementSocketMessageDTO::getEventType)
                .containsExactly("MEASUREMENT_STATUS_CHANGED", "MEASUREMENT_COMPLETED");
        var result = query.get(fixture.userId(), null, null, 0, 20);
        assertThat(result.getSummary()).isEqualTo("Persisted GPT explanation");
        assertThat(result.getSummaryStatus()).isEqualTo("COMPLETED");
        assertThat(result.getSummarySource()).isEqualTo("GPT");
        assertThat(result.getPriorities()).hasSize(3);
        verify(client).generate(any());
    }

    @Test
    void failedGenerationKeepsStoredReportAndTerminatesLoadingWithWarning() throws Exception {
        when(client.generate(any())).thenThrow(new IllegalStateException("API unavailable"));
        var fixture = completeReport();

        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(messaging, timeout(5000).times(2)).convertAndSend(eq(fixture.topic()), messages.capture());
        var last = messages.getAllValues().get(1);
        assertThat(last.getEventType()).isEqualTo("MEASUREMENT_COMPLETED_WITH_WARNING");
        assertThat(last.getShouldDisconnect()).isTrue();
        var result = query.get(fixture.userId(), null, null, 0, 20);
        assertThat(result.isHasMeasurement()).isTrue();
        assertThat(result.getSummaryStatus()).isEqualTo("FAILED");
        assertThat(result.getSummarySource()).isEqualTo("RULE_BASED");
        assertThat(result.getSummary()).isNotBlank();
    }

    @Test
    void rejectedExecutorStillPersistsFailureAndPublishesWarningAfterOriginalCommit() {
        var executor = (ThreadPoolTaskExecutor) recommendationExecutor;
        executor.shutdown();
        try {
            var fixture = completeReport();
            var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
            verify(messaging, times(2)).convertAndSend(eq(fixture.topic()), messages.capture());
            assertThat(messages.getAllValues().get(1).getEventType()).isEqualTo("MEASUREMENT_COMPLETED_WITH_WARNING");
            assertThat(query.get(fixture.userId(), null, null, 0, 20).getSummaryStatus()).isEqualTo("FAILED");
        } finally {
            executor.initialize();
        }
    }

    private Fixture completeReport() {
        return transactions.execute(tx -> {
            var user = em.persist(User.builder().nickname("notification-test").socialId(UUID.randomUUID().toString())
                    .socialType(SocialType.KAKAO).status(UserStatus.ACTIVE).build());
            var device = em.persist(Device.builder().deviceName("notification-" + UUID.randomUUID()).build());
            var session = em.persist(MeasurementSession.builder().user(user).device(device)
                    .status(MeasurementStatus.COMPLETED).measuredAt(LocalDateTime.now()).build());
            var report = em.persist(Report.builder().user(user).measurementSession(session)
                    .reportDate(LocalDateTime.now()).totalScore(80).build());
            for (var type : List.of(MetricType.ATHLETES_FOOT, MetricType.SKIN_IRRITATION)) {
                em.persist(MetricAnalysisResult.builder().report(report).metricType(type)
                        .score(type == MetricType.ATHLETES_FOOT ? 35f : 80f)
                        .status(GaugeStatus.ATTENTION_NEEDED).advice(List.of("test")).build());
            }
            em.persist(HalluxValgusAnalysis.builder().measurementSession(session)
                    .leftToeAngleDegree(18f).rightToeAngleDegree(12f).riskScore(50f).build());
            em.flush();
            socket.sendMeasurementRecommendationPending(session);
            events.publishEvent(new MeasurementCompletedEvent(session.getId(), user.getId()));
            return new Fixture(user.getId(), session.getId());
        });
    }

    private record Fixture(Long userId, Long sessionId) {
        String topic() { return "/topic/measurements/" + sessionId; }
    }
}
