package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.ShoeRecommendationReasonReview;
import com.feetfit.server.domain.ShoeRecommendationRun;
import com.feetfit.server.domain.ShoeReview;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
@Import(MeasurementCommandServiceImpl.class)
class MeasurementDeletionJpaIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MeasurementCommandService measurementCommandService;

    @MockBean
    private MeasurementSocketService measurementSocketService;

    @MockBean
    private MeasurementHardwareClient measurementHardwareClient;

    @MockBean
    private MeasurementCompletionService measurementCompletionService;

    private User user;
    private Device device;

    @BeforeEach
    void setUp() {
        device = entityManager.persist(Device.builder().deviceName("deletion-device").build());
        user = entityManager.persist(User.builder()
                .nickname("deletion-user")
                .socialType(SocialType.KAKAO)
                .socialId("deletion-social")
                .build());
    }

    @Test
    void deleteMeasurementRecords_deletesRecommendationTreeAndPreservesOtherSessionAndSourceData() {
        Shoe shoe = entityManager.persist(Shoe.builder()
                .brandName("brand")
                .shoeName("shoe")
                .modelCode("deletion-model")
                .musinsaGoodsNo("deletion-goods")
                .musinsaUrl("https://example.com/shoe")
                .build());
        ShoeReview review = entityManager.persist(ShoeReview.builder()
                .shoe(shoe)
                .rating(5f)
                .reviewText("shared review")
                .sourceReviewId("deletion-review")
                .contentHash("deletion-review-hash")
                .source(ShoeReviewSource.MUSINSA)
                .collectedAt(LocalDateTime.now())
                .build());
        MeasurementSession target = session();
        MeasurementSession other = session();
        ShoeRecommendation targetRecommendation = recommendation(target, shoe, review);
        ShoeRecommendation otherRecommendation = recommendation(other, shoe, review);
        ShoeRecommendationRun targetRun = run(target);
        ShoeRecommendationRun otherRun = run(other);
        DailyFootAnalysis targetAnalysis = analysis(target);
        DailyFootAnalysis otherAnalysis = analysis(other);
        entityManager.flush();
        entityManager.clear();

        MeasurementResponseDTO.DeleteMeasurementRecordsResultDTO result =
                measurementCommandService.deleteMeasurementRecords(user.getId(), target.getId());
        entityManager.clear();

        assertThat(result.getMeasurementSessionId()).isEqualTo(target.getId());
        assertThat(result.getDeletedShoeRecommendationReasonReviewCount()).isEqualTo(3);
        assertThat(result.getDeletedShoeRecommendationReasonCount()).isEqualTo(3);
        assertThat(result.getDeletedShoeRecommendationCount()).isOne();
        assertThat(result.getDeletedShoeRecommendationRunCount()).isOne();
        assertThat(result.getDeletedDailyFootAnalysisCount()).isOne();
        assertThat(result.getDeletedMeasurementSessionCount()).isOne();
        assertThat(entityManager.find(MeasurementSession.class, target.getId())).isNull();
        assertThat(entityManager.find(ShoeRecommendation.class, targetRecommendation.getId())).isNull();
        assertThat(entityManager.find(ShoeRecommendationRun.class, targetRun.getId())).isNull();
        assertThat(entityManager.find(DailyFootAnalysis.class, targetAnalysis.getId())).isNull();

        assertThat(entityManager.find(MeasurementSession.class, other.getId())).isNotNull();
        assertThat(entityManager.find(ShoeRecommendationRun.class, otherRun.getId())).isNotNull();
        assertThat(entityManager.find(DailyFootAnalysis.class, otherAnalysis.getId())).isNotNull();
        ShoeRecommendation remaining = entityManager.find(ShoeRecommendation.class, otherRecommendation.getId());
        assertThat(remaining).isNotNull();
        assertThat(remaining.getReasons()).hasSize(3).allSatisfy(reason ->
                assertThat(reason.getReasonReviews()).singleElement().satisfies(link ->
                        assertThat(link.getReview().getId()).isEqualTo(review.getId())));
        assertThat(entityManager.find(Shoe.class, shoe.getId())).isNotNull();
        assertThat(entityManager.find(ShoeReview.class, review.getId())).isNotNull();
        assertThat(entityManager.find(User.class, user.getId())).isNotNull();
        assertThat(entityManager.find(Device.class, device.getId())).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deleteMeasurementRecords_withoutRecommendations_deletesSessionAndOptionalRun(boolean hasRun) {
        MeasurementSession target = session();
        ShoeRecommendationRun recommendationRun = hasRun ? run(target) : null;
        entityManager.flush();
        entityManager.clear();

        MeasurementResponseDTO.DeleteMeasurementRecordsResultDTO result =
                measurementCommandService.deleteMeasurementRecords(user.getId(), target.getId());
        entityManager.clear();

        assertThat(result.getDeletedShoeRecommendationReasonReviewCount()).isZero();
        assertThat(result.getDeletedShoeRecommendationReasonCount()).isZero();
        assertThat(result.getDeletedShoeRecommendationCount()).isZero();
        assertThat(result.getDeletedShoeRecommendationRunCount()).isEqualTo(hasRun ? 1 : 0);
        assertThat(result.getDeletedMeasurementSessionCount()).isOne();
        assertThat(entityManager.find(MeasurementSession.class, target.getId())).isNull();
        if (hasRun) {
            assertThat(entityManager.find(ShoeRecommendationRun.class, recommendationRun.getId())).isNull();
        }
    }

    @Test
    void deleteMeasurementRecords_otherUser_cannotDeleteSessionOrRelatedRecords() {
        User otherUser = entityManager.persist(User.builder()
                .nickname("other-user")
                .socialType(SocialType.KAKAO)
                .socialId("other-social")
                .build());
        MeasurementSession target = session();
        ShoeRecommendationRun recommendationRun = run(target);
        DailyFootAnalysis analysis = analysis(target);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> measurementCommandService.deleteMeasurementRecords(otherUser.getId(), target.getId()))
                .isInstanceOf(MeasurementHandler.class)
                .satisfies(error -> assertThat(((MeasurementHandler) error).getCode())
                        .isEqualTo(ErrorStatus.MEASUREMENT_FORBIDDEN));
        entityManager.clear();

        assertThat(entityManager.find(MeasurementSession.class, target.getId())).isNotNull();
        assertThat(entityManager.find(ShoeRecommendationRun.class, recommendationRun.getId())).isNotNull();
        assertThat(entityManager.find(DailyFootAnalysis.class, analysis.getId())).isNotNull();
    }

    @Test
    void deleteMeasurementRecords_missingSession_returnsNotFound() {
        assertThatThrownBy(() -> measurementCommandService.deleteMeasurementRecords(user.getId(), Long.MAX_VALUE))
                .isInstanceOf(MeasurementHandler.class)
                .satisfies(error -> assertThat(((MeasurementHandler) error).getCode())
                        .isEqualTo(ErrorStatus.MEASUREMENT_NOT_FOUND));
    }

    private MeasurementSession session() {
        return entityManager.persist(MeasurementSession.builder()
                .user(user)
                .device(device)
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(LocalDateTime.now())
                .build());
    }

    private DailyFootAnalysis analysis(MeasurementSession session) {
        return entityManager.persist(DailyFootAnalysis.builder()
                .measurementSession(session)
                .balanceScore(80f)
                .build());
    }

    private ShoeRecommendationRun run(MeasurementSession session) {
        return entityManager.persist(ShoeRecommendationRun.builder()
                .measurementSession(session)
                .expectedCount(1)
                .build());
    }

    private ShoeRecommendation recommendation(MeasurementSession session, Shoe shoe, ShoeReview review) {
        ShoeRecommendation recommendation = entityManager.persist(ShoeRecommendation.builder()
                .user(user)
                .shoe(shoe)
                .measurementSession(session)
                .fitScore(80f)
                .analyzedAt(LocalDateTime.now())
                .build());
        for (ReasonType type : new ReasonType[]{ReasonType.FOREFOOT, ReasonType.HEEL, ReasonType.INSOLE}) {
            ShoeRecommendationReason reason = entityManager.persist(ShoeRecommendationReason.builder()
                    .shoeRecommendation(recommendation)
                    .reasonType(type)
                    .title(type.name())
                    .riskLevel(RiskLevel.LOW)
                    .build());
            entityManager.persist(ShoeRecommendationReasonReview.builder().reason(reason).review(review).build());
        }
        return recommendation;
    }
}
