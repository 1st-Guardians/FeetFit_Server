package com.feetfit.server.service.ShoeService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.repository.*;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import com.feetfit.server.domain.enums.ShoeSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:recommendation-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.connection-init-sql=",
        "jwt.secret=ZmFrZS10ZXN0LWp3dC1zZWNyZXQtZmFrZS10ZXN0LWp3dC1zZWNyZXQ=",
        "INTERNAL_API_KEY=test-internal-key"
})
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShoeRecommendationSessionServiceJpaIntegrationTest {

    @Autowired ShoeCommandService shoeCommandService;
    @Autowired ShoeQueryService shoeQueryService;
    @Autowired ShoeSearchQueryService shoeSearchQueryService;
    @Autowired ShoeAnalysisQueryService shoeAnalysisQueryService;
    @Autowired ShoeRecommendationSessionResolver recommendationSessionResolver;
    @Autowired ShoeRecommendationRunService runService;
    @Autowired UserRepository userRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired MeasurementSessionRepository measurementSessionRepository;
    @Autowired ShoeRepository shoeRepository;
    @Autowired ShoeRecommendationRepository recommendationRepository;
    @Autowired ShoeRecommendationRunRepository recommendationRunRepository;
    @Autowired ShoeRecommendationReasonRepository reasonRepository;
    @Autowired ShoeRecommendationReasonReviewRepository reasonReviewRepository;
    @Autowired ShoeReviewRepository shoeReviewRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User user;
    private Device device;
    private Shoe shoe;
    private MeasurementSession firstSession;
    private MeasurementSession secondSession;

    @BeforeEach
    void setUp() {
        device = deviceRepository.save(Device.builder().deviceName("service-device").build());
        user = userRepository.save(User.builder()
                .nickname("service-user")
                .socialType(SocialType.KAKAO)
                .socialId("service-social")
                .build());
        shoe = shoeRepository.save(Shoe.builder()
                .brandName("brand")
                .shoeName("shoe")
                .modelCode("model")
                .musinsaGoodsNo("service-goods")
                .musinsaUrl("https://example.com/service-shoe")
                .build());
        firstSession = measurementSessionRepository.save(completedSession(
                LocalDateTime.now().minusMinutes(1)));
        secondSession = measurementSessionRepository.save(completedSession(LocalDateTime.now()));
        runService.startRun(user.getId(), firstSession.getId(), 3, false);
        runService.startRun(user.getId(), secondSession.getId(), 3, false);
    }

    @Test
    void replayInSameSessionUpdatesOneRecommendationAndKeepsThreeReasons() throws Exception {
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(firstSession.getId(), shoe.getId(), 80));
        ShoeRecommendation first = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(firstSession.getId(), shoe.getId())
                .orElseThrow();
        Long recommendationId = first.getId();

        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(firstSession.getId(), shoe.getId(), 91));
        ShoeRecommendation replayed = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(firstSession.getId(), shoe.getId())
                .orElseThrow();

        assertThat(recommendationRepository.countByMeasurementSessionId(firstSession.getId())).isOne();
        assertThat(replayed.getId()).isEqualTo(recommendationId);
        assertThat(replayed.getFitScore()).isEqualTo(91f);
        assertThat(reasonRepository.findByShoeRecommendationId(recommendationId))
                .extracting(ShoeRecommendationReason::getReasonType)
                .containsExactlyInAnyOrder(
                        com.feetfit.server.domain.enums.ReasonType.FOREFOOT,
                        com.feetfit.server.domain.enums.ReasonType.HEEL,
                        com.feetfit.server.domain.enums.ReasonType.INSOLE);
    }

    @Test
    void sameShoeInDifferentSessionCreatesHistoricalRecommendation() throws Exception {
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(firstSession.getId(), shoe.getId(), 80));
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(secondSession.getId(), shoe.getId(), 95));

        ShoeRecommendation oldRecommendation = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(firstSession.getId(), shoe.getId())
                .orElseThrow();
        ShoeRecommendation latestRecommendation = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(secondSession.getId(), shoe.getId())
                .orElseThrow();

        assertThat(oldRecommendation.getId()).isNotEqualTo(latestRecommendation.getId());
        assertThat(oldRecommendation.getFitScore()).isEqualTo(80f);
        assertThat(latestRecommendation.getFitScore()).isEqualTo(95f);
    }

    @Test
    void summarySaveChangesOnlyExplicitSession() throws Exception {
        Shoe secondShoe = shoeRepository.save(shoe("summary-goods-2", "summary-model-2"));
        Shoe thirdShoe = shoeRepository.save(shoe("summary-goods-3", "summary-model-3"));
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(firstSession.getId(), shoe.getId(), 80));
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(firstSession.getId(), secondShoe.getId(), 70));
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(firstSession.getId(), thirdShoe.getId(), 60));
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(secondSession.getId(), shoe.getId(), 95));
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(secondSession.getId(), secondShoe.getId(), 85));
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(secondSession.getId(), thirdShoe.getId(), 75));
        runService.completeRun(user.getId(), firstSession.getId());
        runService.completeRun(user.getId(), secondSession.getId());

        com.feetfit.server.web.dto.shoe.ShoeAnalysisResponseDTO.RecommendationSummaryContext
                firstContext = shoeAnalysisQueryService.getRecommendationSummaryContext(
                user.getId(), firstSession.getId(), shoe.getId());
        com.feetfit.server.web.dto.shoe.ShoeAnalysisResponseDTO.RecommendationSummaryContext
                secondContext = shoeAnalysisQueryService.getRecommendationSummaryContext(
                user.getId(), secondSession.getId(), shoe.getId());
        assertThat(firstContext.getMeasurementSessionId()).isEqualTo(firstSession.getId());
        assertThat(firstContext.getFitScore()).isEqualTo(80f);
        assertThat(secondContext.getMeasurementSessionId()).isEqualTo(secondSession.getId());
        assertThat(secondContext.getFitScore()).isEqualTo(95f);

        shoeCommandService.saveShoeSummaries(
                user.getId(), shoe.getId(), summaryRequest(firstSession.getId()));

        ShoeRecommendation oldRecommendation = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(firstSession.getId(), shoe.getId())
                .orElseThrow();
        ShoeRecommendation latestRecommendation = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(secondSession.getId(), shoe.getId())
                .orElseThrow();
        assertThat(oldRecommendation.getPointSummary()).isEqualTo("explicit summary");
        assertThat(latestRecommendation.getPointSummary()).isEqualTo("score summary");
    }

    @Test
    void summaryReviewIdsOutsideBgeCandidatesFailClosed() throws Exception {
        Shoe secondShoe = shoeRepository.save(shoe("subset-goods-2", "subset-model-2"));
        Shoe thirdShoe = shoeRepository.save(shoe("subset-goods-3", "subset-model-3"));
        save(firstSession, shoe, 80);
        save(firstSession, secondShoe, 70);
        save(firstSession, thirdShoe, 60);
        runService.completeRun(user.getId(), firstSession.getId());

        ShoeRequestDTO.SaveShoeSummariesDTO invalid = objectMapper.readValue("""
                {"measurementSessionId":%d,"pointSummary":"must not persist","reasons":[
                  {"reasonType":"FOREFOOT","reviewSummary":"invalid","reviewIds":[999999]},
                  {"reasonType":"HEEL","reviewSummary":"heel","reviewIds":[]},
                  {"reasonType":"INSOLE","reviewSummary":"insole","reviewIds":[]}
                ]}
                """.formatted(firstSession.getId()),
                ShoeRequestDTO.SaveShoeSummariesDTO.class);

        assertThatThrownBy(() -> shoeCommandService.saveShoeSummaries(
                user.getId(), shoe.getId(), invalid)).isInstanceOf(RuntimeException.class);

        ShoeRecommendation unchanged = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(firstSession.getId(), shoe.getId())
                .orElseThrow();
        assertThat(unchanged.getPointSummary()).isEqualTo("score summary");
        assertThat(reasonRepository.findByShoeRecommendationId(unchanged.getId()))
                .allMatch(reason -> reason.getReviewSummary() == null);
    }

    @Test
    void completedSessionSummaryReplacesLinksWithBgeCandidateSubset() throws Exception {
        Shoe secondShoe = shoeRepository.save(shoe("selected-goods-2", "selected-model-2"));
        Shoe thirdShoe = shoeRepository.save(shoe("selected-goods-3", "selected-model-3"));
        ShoeReview firstReview = shoeReviewRepository.save(review("review-1", "hash-1"));
        ShoeReview secondReview = shoeReviewRepository.save(review("review-2", "hash-2"));

        ShoeRequestDTO.SaveShoeRecommendationDTO candidates = objectMapper.readValue("""
                {"measurementSessionId":%d,"recommendations":[{
                  "shoeId":%d,"fitScore":80,"pointSummary":null,"reasons":[
                    {"reasonType":"FOREFOOT","title":"forefoot","riskLevel":"LOW","reviewIds":[%d,%d]},
                    {"reasonType":"HEEL","title":"heel","riskLevel":"MEDIUM","reviewIds":[%d,%d]},
                    {"reasonType":"INSOLE","title":"insole","riskLevel":"HIGH","reviewIds":[%d,%d]}
                  ]
                }]}
                """.formatted(
                        firstSession.getId(), shoe.getId(),
                        firstReview.getId(), secondReview.getId(),
                        firstReview.getId(), secondReview.getId(),
                        firstReview.getId(), secondReview.getId()),
                ShoeRequestDTO.SaveShoeRecommendationDTO.class);
        shoeCommandService.saveShoeRecommendations(user.getId(), candidates);
        save(firstSession, secondShoe, 70);
        save(firstSession, thirdShoe, 60);
        runService.completeRun(user.getId(), firstSession.getId());

        ShoeRequestDTO.SaveShoeSummariesDTO selected = objectMapper.readValue("""
                {"measurementSessionId":%d,"pointSummary":"ollama point","reasons":[
                  {"reasonType":"FOREFOOT","reviewSummary":"forefoot final","reviewIds":[%d]},
                  {"reasonType":"HEEL","reviewSummary":"heel final","reviewIds":[]},
                  {"reasonType":"INSOLE","reviewSummary":"insole final","reviewIds":[%d,%d]}
                ]}
                """.formatted(
                        firstSession.getId(), secondReview.getId(),
                        firstReview.getId(), secondReview.getId()),
                ShoeRequestDTO.SaveShoeSummariesDTO.class);
        shoeCommandService.saveShoeSummaries(user.getId(), shoe.getId(), selected);

        ShoeRecommendation recommendation = recommendationRepository
                .findByMeasurementSessionIdAndShoeId(firstSession.getId(), shoe.getId())
                .orElseThrow();
        assertThat(recommendation.getPointSummary()).isEqualTo("ollama point");
        java.util.Map<com.feetfit.server.domain.enums.ReasonType, java.util.List<Long>> links =
                reasonRepository.findByShoeRecommendationId(recommendation.getId()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ShoeRecommendationReason::getReasonType,
                                reason -> reasonReviewRepository.findByReasonId(reason.getId()).stream()
                                        .map(link -> link.getReview().getId())
                                        .sorted()
                                        .toList()));
        assertThat(links.get(com.feetfit.server.domain.enums.ReasonType.FOREFOOT))
                .containsExactly(secondReview.getId());
        assertThat(links.get(com.feetfit.server.domain.enums.ReasonType.HEEL)).isEmpty();
        assertThat(links.get(com.feetfit.server.domain.enums.ReasonType.INSOLE))
                .containsExactly(firstReview.getId(), secondReview.getId());
    }

    @Test
    void fitPaginationDetailAndTop3UseOneCompletedSessionWithoutHistoryMixing() throws Exception {
        Shoe secondShoe = shoeRepository.save(shoe("service-goods-2", "model-2"));
        Shoe thirdShoe = shoeRepository.save(shoe("service-goods-3", "model-3"));

        save(firstSession, shoe, 99);
        save(firstSession, secondShoe, 50);
        save(firstSession, thirdShoe, 10);
        runService.completeRun(user.getId(), firstSession.getId());

        save(secondSession, shoe, 1);
        save(secondSession, secondShoe, 60);
        save(secondSession, thirdShoe, 100);
        runService.completeRun(user.getId(), secondSession.getId());

        ShoeResponseDTO.ShoeListResultDTO currentFirstPage = shoeQueryService.getShoeList(
                user.getId(), new ShoeRequestDTO.ShoeListRequestDTO(ShoeSort.FIT_SCORE, 0, 2, null));
        ShoeResponseDTO.ShoeListResultDTO currentSecondPage = shoeQueryService.getShoeList(
                user.getId(), new ShoeRequestDTO.ShoeListRequestDTO(ShoeSort.FIT_SCORE, 1, 2, null));

        assertThat(currentFirstPage.getMeasurementSessionId()).isEqualTo(secondSession.getId());
        assertThat(currentFirstPage.getTotalElements()).isEqualTo(3);
        assertThat(currentFirstPage.getShoes()).extracting(ShoeResponseDTO.ShoeItemDTO::getId)
                .containsExactly(thirdShoe.getId(), secondShoe.getId());
        assertThat(currentSecondPage.getShoes()).extracting(ShoeResponseDTO.ShoeItemDTO::getId)
                .containsExactly(shoe.getId());
        assertThat(java.util.stream.Stream.concat(
                        currentFirstPage.getShoes().stream(), currentSecondPage.getShoes().stream())
                .map(ShoeResponseDTO.ShoeItemDTO::getId).toList()).doesNotHaveDuplicates();

        ShoeResponseDTO.ShoeListResultDTO explicitOld = shoeQueryService.getShoeList(
                user.getId(), new ShoeRequestDTO.ShoeListRequestDTO(
                        ShoeSort.FIT_SCORE, 0, 3, firstSession.getId()));
        assertThat(explicitOld.getMeasurementSessionId()).isEqualTo(firstSession.getId());
        assertThat(explicitOld.getShoes()).extracting(ShoeResponseDTO.ShoeItemDTO::getId)
                .containsExactly(shoe.getId(), secondShoe.getId(), thirdShoe.getId());

        ShoeResponseDTO.ShoeDetailResultDTO currentDetail = shoeSearchQueryService
                .getShoeDetail(user.getId(), shoe.getId(), null);
        ShoeResponseDTO.ShoeDetailResultDTO oldDetail = shoeSearchQueryService
                .getShoeDetail(user.getId(), shoe.getId(), firstSession.getId());
        assertThat(currentDetail.getMeasurementSessionId()).isEqualTo(secondSession.getId());
        assertThat(currentDetail.getFitScore()).isEqualTo(1f);
        assertThat(oldDetail.getMeasurementSessionId()).isEqualTo(firstSession.getId());
        assertThat(oldDetail.getFitScore()).isEqualTo(99f);

        ShoeResponseDTO.ShoeRecommendTop3ResultDTO top3 = shoeQueryService
                .getTop3ShoesByFitScore(user.getId(), null);
        assertThat(top3.getMeasurementSessionId()).isEqualTo(secondSession.getId());
        assertThat(top3.getShoes()).extracting(ShoeResponseDTO.ShoeRecommendTop3ItemDTO::getId)
                .containsExactly(thirdShoe.getId(), secondShoe.getId(), shoe.getId());
    }

    @Test
    void runningAndFailedNewerRunAreNotSelectedAsCurrent() throws Exception {
        Shoe secondShoe = shoeRepository.save(shoe("status-goods-2", "status-model-2"));
        Shoe thirdShoe = shoeRepository.save(shoe("status-goods-3", "status-model-3"));
        save(firstSession, shoe, 80);
        save(firstSession, secondShoe, 70);
        save(firstSession, thirdShoe, 60);
        runService.completeRun(user.getId(), firstSession.getId());

        assertThat(recommendationSessionResolver.requireCurrentCompleted(user.getId())
                .measurementSessionId()).isEqualTo(firstSession.getId());

        recommendationRunRepository.findByMeasurementSessionId(secondSession.getId())
                .orElseThrow()
                .fail("expected test failure");
        assertThat(recommendationSessionResolver.requireCurrentCompleted(user.getId())
                .measurementSessionId()).isEqualTo(firstSession.getId());
    }

    private MeasurementSession completedSession(LocalDateTime measuredAt) {
        return MeasurementSession.builder()
                .user(user)
                .device(device)
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(measuredAt)
                .build();
    }

    private Shoe shoe(String goodsNo, String modelCode) {
        return Shoe.builder()
                .brandName("brand")
                .shoeName("shoe-" + modelCode)
                .modelCode(modelCode)
                .musinsaGoodsNo(goodsNo)
                .musinsaUrl("https://example.com/" + goodsNo)
                .build();
    }

    private ShoeReview review(String sourceReviewId, String contentHash) {
        return ShoeReview.builder()
                .shoe(shoe)
                .rating(5f)
                .reviewText(sourceReviewId + " text")
                .sourceReviewId(sourceReviewId)
                .contentHash(contentHash)
                .source(ShoeReviewSource.MUSINSA)
                .collectedAt(LocalDateTime.now())
                .build();
    }

    private void save(MeasurementSession session, Shoe targetShoe, int score) throws Exception {
        shoeCommandService.saveShoeRecommendations(
                user.getId(), recommendationRequest(session.getId(), targetShoe.getId(), score));
    }

    private ShoeRequestDTO.SaveShoeRecommendationDTO recommendationRequest(
            Long sessionId, Long shoeId, int score) throws Exception {
        return objectMapper.readValue("""
                {"measurementSessionId":%d,"recommendations":[{
                  "shoeId":%d,"fitScore":%d,"pointSummary":"score summary","reasons":[
                    {"reasonType":"FOREFOOT","title":"forefoot","riskLevel":"LOW","reviewIds":[]},
                    {"reasonType":"HEEL","title":"heel","riskLevel":"MEDIUM","reviewIds":[]},
                    {"reasonType":"INSOLE","title":"insole","riskLevel":"HIGH","reviewIds":[]}
                  ]
                }]}
                """.formatted(sessionId, shoeId, score),
                ShoeRequestDTO.SaveShoeRecommendationDTO.class);
    }

    private ShoeRequestDTO.SaveShoeSummariesDTO summaryRequest(Long sessionId) throws Exception {
        return objectMapper.readValue("""
                {"measurementSessionId":%d,"pointSummary":"explicit summary","reasons":[
                  {"reasonType":"FOREFOOT","reviewSummary":"forefoot summary","reviewIds":[]},
                  {"reasonType":"HEEL","reviewSummary":"heel summary","reviewIds":[]},
                  {"reasonType":"INSOLE","reviewSummary":"insole summary","reviewIds":[]}
                ]}
                """.formatted(sessionId), ShoeRequestDTO.SaveShoeSummariesDTO.class);
    }
}
