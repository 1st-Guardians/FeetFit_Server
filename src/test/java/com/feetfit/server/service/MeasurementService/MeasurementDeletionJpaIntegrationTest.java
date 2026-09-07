package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.ReportHandler;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.ShoeRecommendationReasonReview;
import com.feetfit.server.domain.ShoeRecommendationRun;
import com.feetfit.server.domain.ShoeReview;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.service.ReportService.ReportQueryService;
import com.feetfit.server.service.ReportService.ReportQueryServiceImpl;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
@Import({MeasurementCommandServiceImpl.class, ReportQueryServiceImpl.class})
class MeasurementDeletionJpaIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MeasurementCommandService measurementCommandService;

    @Autowired
    private ReportQueryService reportQueryService;

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

    @Test
    void deleteOnlyMeasurement_removesSummaryMetricsAndDailyAnalysis() {
        MeasurementSession target = session();
        Report report = report(target, 80f, LocalDate.now().atTime(12, 0));
        DailyFootAnalysis analysis = analysis(target);
        entityManager.flush();
        entityManager.clear();
        assertThat(reportQueryService.getReportSummary(user.getId()).getTotalScore()).isEqualTo(80);
        assertThat(reportQueryService.getDailyFootAnalysis(user.getId(), LocalDate.now()).getMeasurementSessionId())
                .isEqualTo(target.getId());
        entityManager.clear();

        var deleted = measurementCommandService.deleteMeasurementRecords(user.getId(), target.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted.getDeletedReportCount()).isOne();
        assertThat(deleted.getDeletedMetricAnalysisResultCount()).isEqualTo(5);
        assertThat(deleted.getDeletedDailyFootAnalysisCount()).isOne();
        assertThat(entityManager.find(Report.class, report.getId())).isNull();
        assertThat(entityManager.find(DailyFootAnalysis.class, analysis.getId())).isNull();
        assertThat(entityManager.getEntityManager().createQuery(
                "SELECT COUNT(m) FROM MetricAnalysisResult m WHERE m.report.id = :reportId", Long.class)
                .setParameter("reportId", report.getId()).getSingleResult()).isZero();
        assertThatThrownBy(() -> reportQueryService.getReportSummary(user.getId()))
                .isInstanceOf(ReportHandler.class)
                .satisfies(error -> assertThat(((ReportHandler) error).getCode()).isEqualTo(ErrorStatus.REPORT_NOT_FOUND));
        assertThatThrownBy(() -> reportQueryService.getDailyFootAnalysis(user.getId(), LocalDate.now()))
                .isInstanceOf(ReportHandler.class)
                .satisfies(error -> assertThat(((ReportHandler) error).getCode()).isEqualTo(ErrorStatus.REPORT_NOT_FOUND));
    }

    @Test
    void deleteLatestMeasurement_summaryReturnsRemainingMeasurementFromSameDay() {
        MeasurementSession previous = session();
        Report previousReport = report(previous, 60f, LocalDate.now().atTime(10, 0));
        analysis(previous);
        MeasurementSession target = session();
        Report targetReport = report(target, 90f, LocalDate.now().atTime(12, 0));
        analysis(target);
        entityManager.flush();
        entityManager.clear();
        assertThat(reportQueryService.getReportSummary(user.getId()).getTotalScore()).isEqualTo(90);
        entityManager.clear();

        measurementCommandService.deleteMeasurementRecords(user.getId(), target.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Report.class, targetReport.getId())).isNull();
        assertThat(entityManager.find(Report.class, previousReport.getId())).isNotNull();
        var summary = reportQueryService.getReportSummary(user.getId());
        assertThat(summary.getTotalScore()).isEqualTo(60);
        assertThat(summary.getMetricScores()).hasSize(5)
                .allSatisfy(metric -> assertThat(metric.getScore()).isEqualTo(60f));
        assertThat(summary.getMonthlyScores()).singleElement()
                .satisfies(month -> assertThat(month.getAvgScore()).isEqualTo(60f));
        assertThat(reportQueryService.getDailyFootAnalysis(user.getId(), LocalDate.now()).getMeasurementSessionId())
                .isEqualTo(previous.getId());
    }

    private Report report(MeasurementSession session, float score, LocalDateTime reportDate) {
        Report report = entityManager.persist(Report.builder()
                .measurementSession(session).user(user).reportDate(reportDate).totalScore(Math.round(score)).build());
        for (MetricType metric : List.of(MetricType.PRESSURE_BALANCE, MetricType.HALLUX_VALGUS,
                MetricType.ATHLETES_FOOT, MetricType.SKIN_IRRITATION, MetricType.FOOT_ENVIRONMENT)) {
            entityManager.persist(MetricAnalysisResult.builder().report(report).metricType(metric)
                    .score(score).status(GaugeStatus.VERY_GOOD).advice(List.of("test advice")).build());
        }
        return report;
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
