package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.exception.handler.ReportHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.TinaPedisAnalysis;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.DailyFootAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.ReportRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceImplTest {

    @Mock
    private HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;

    @Mock
    private TinaPedisAnalysisRepository tinaPedisAnalysisRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DailyFootAnalysisRepository dailyFootAnalysisRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MeasurementSessionRepository measurementSessionRepository;

    @InjectMocks
    private ReportQueryServiceImpl reportQueryService;

    @Test
    void getReportSummary_withoutTodayReport_returnsLatestPastReport() {
        Report pastReport = completeReport(LocalDateTime.now().minusDays(2));
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(reportRepository
                .findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
                        eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Optional.empty());
        given(reportRepository.findTopByUserIdOrderByReportDateDesc(1L))
                .willReturn(Optional.of(pastReport));
        given(reportRepository.findByUserIdAndReportDateBetween(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(pastReport));

        ReportResponseDTO.ReportSummaryResultDTO response = reportQueryService.getReportSummary(1L);

        assertThat(response.getTotalScore()).isEqualTo(70);
        assertThat(response.getMetricScores()).hasSize(5);
        then(reportRepository).should().findTopByUserIdOrderByReportDateDesc(1L);
    }

    @Test
    void getReportSummary_withoutAnyReport_throwsReportNotFound() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(reportRepository
                .findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
                        eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Optional.empty());
        given(reportRepository.findTopByUserIdOrderByReportDateDesc(1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reportQueryService.getReportSummary(1L))
                .isInstanceOf(ReportHandler.class);
    }

    @Test
    void getTinaPedisAnalysis_existingAnalysis_returnsAnalysis() {
        TinaPedisAnalysis currentAnalysis = tinaPedisAnalysis(
                1L,
                82,
                76,
                LocalDateTime.of(2026, 5, 20, 9, 0)
        );
        TinaPedisAnalysis previousAnalysis = tinaPedisAnalysis(
                2L,
                70,
                60,
                LocalDateTime.of(2026, 5, 19, 9, 0)
        );

        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0),
                        LocalDateTime.of(2026, 5, 21, 0, 0)
                ))
                .willReturn(Optional.of(currentAnalysis));
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0)
                ))
                .willReturn(Optional.of(previousAnalysis));

        ReportResponseDTO.TinaPedisAnalysisResultDTO response =
                reportQueryService.getTinaPedisAnalysis(1L, LocalDate.of(2026, 5, 20));

        assertThat(response.getMeasurementSessionId()).isEqualTo(1L);
        assertThat(response.getFungalSuspicionSafetyScore()).isEqualTo(82);
        assertThat(response.getSkinReactionSafetyScore()).isEqualTo(76);
        assertThat(response.getTotalScore()).isEqualTo(80.2f);
        assertThat(response.getPreviousTotalScore()).isEqualTo(67.0f);
        assertThat(response.getTotalScoreDiff()).isEqualTo(13.2f);
    }

    @Test
    void getTinaPedisAnalysis_hasTodayOlderScore100AndYesterdayScore93_comparesWithYesterdayScore() {
        TinaPedisAnalysis currentAnalysis = tinaPedisAnalysis(
                1L,
                80,
                80,
                LocalDateTime.of(2026, 5, 20, 9, 0)
        );
        TinaPedisAnalysis immediatePreviousAnalysis = tinaPedisAnalysis(
                2L,
                93,
                93,
                LocalDateTime.of(2026, 5, 19, 9, 0)
        );
        TinaPedisAnalysis todayOlderAnalysisWithScore100 = tinaPedisAnalysis(
                3L,
                100,
                100,
                LocalDateTime.of(2026, 5, 20, 8, 0)
        );
        TinaPedisAnalysis olderAnalysisWithScore100 = tinaPedisAnalysis(
                4L,
                100,
                100,
                LocalDateTime.of(2026, 5, 18, 9, 0)
        );

        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0),
                        LocalDateTime.of(2026, 5, 21, 0, 0)
                ))
                .willReturn(Optional.of(currentAnalysis));
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0)
                ))
                .willAnswer(invocation -> {
                    LocalDateTime currentDayStart = invocation.getArgument(1);
                    return List.of(todayOlderAnalysisWithScore100, immediatePreviousAnalysis, olderAnalysisWithScore100).stream()
                            .filter(analysis -> analysis.getRecordedAt().isBefore(currentDayStart))
                            .max(Comparator.comparing(TinaPedisAnalysis::getRecordedAt));
                });

        ReportResponseDTO.TinaPedisAnalysisResultDTO response =
                reportQueryService.getTinaPedisAnalysis(1L, LocalDate.of(2026, 5, 20));

        assertThat(response.getTotalScore()).isEqualTo(80.0f);
        assertThat(response.getPreviousTotalScore()).isEqualTo(93.0f);
        assertThat(response.getTotalScoreDiff()).isEqualTo(-13.0f);

        then(tinaPedisAnalysisRepository).should()
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0)
                );
    }

    @Test
    void getTinaPedisAnalysis_noPastAnalysis_returnsNullPreviousScoreAndDiff() {
        TinaPedisAnalysis currentAnalysis = tinaPedisAnalysis(
                1L,
                80,
                80,
                LocalDateTime.of(2026, 5, 20, 9, 0)
        );

        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0),
                        LocalDateTime.of(2026, 5, 21, 0, 0)
                ))
                .willReturn(Optional.of(currentAnalysis));
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0)
                ))
                .willReturn(Optional.empty());

        ReportResponseDTO.TinaPedisAnalysisResultDTO response =
                reportQueryService.getTinaPedisAnalysis(1L, LocalDate.of(2026, 5, 20));

        assertThat(response.getTotalScore()).isEqualTo(80.0f);
        assertThat(response.getPreviousTotalScore()).isNull();
        assertThat(response.getTotalScoreDiff()).isNull();
    }

    @Test
    void getTinaPedisAnalysis_missingAnalysis_throwsReportHandler() {
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0),
                        LocalDateTime.of(2026, 5, 21, 0, 0)
                ))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reportQueryService.getTinaPedisAnalysis(1L, LocalDate.of(2026, 5, 20)))
                .isInstanceOf(ReportHandler.class);
    }

    private static TinaPedisAnalysis tinaPedisAnalysis(
            Long id,
            Integer fungalSuspicionSafetyScore,
            Integer skinReactionSafetyScore,
            LocalDateTime recordedAt
    ) {
        return TinaPedisAnalysis.builder()
                .id(id)
                .measurementSession(measurementSession())
                .fungalSuspicionSafetyScore(fungalSuspicionSafetyScore)
                .skinReactionSafetyScore(skinReactionSafetyScore)
                .fungalSuspicionSafetyDescription("발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.")
                .skinReactionSafetyDescription("피부 발적과 자극 반응은 경미한 수준입니다.")
                .totalScoreDescription("전반적으로 안전하지만 발 건조 관리가 필요합니다.")
                .suspiciousAreaMapImageUrl("https://example.com/tina-pedis/map.png")
                .originalFootImageUrl("https://example.com/tina-pedis/original.png")
                .recordedAt(recordedAt)
                .build();
    }

    private static MeasurementSession measurementSession() {
        return MeasurementSession.builder()
                .id(1L)
                .user(user())
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(LocalDateTime.of(2026, 5, 20, 8, 0))
                .build();
    }

    private static Report completeReport(LocalDateTime reportDate) {
        Report report = Report.builder()
                .id(1L)
                .measurementSession(measurementSession())
                .user(user())
                .reportDate(reportDate)
                .totalScore(70)
                .build();
        List<MetricAnalysisResult> results = List.of(
                metricResult(report, MetricType.PRESSURE_BALANCE),
                metricResult(report, MetricType.HALLUX_VALGUS),
                metricResult(report, MetricType.ATHLETES_FOOT),
                metricResult(report, MetricType.SKIN_IRRITATION),
                metricResult(report, MetricType.FOOT_ENVIRONMENT));
        report.getMetricAnalysisResults().addAll(results);
        return report;
    }

    private static MetricAnalysisResult metricResult(Report report, MetricType metricType) {
        return MetricAnalysisResult.builder()
                .report(report)
                .metricType(metricType)
                .score(70.0f)
                .status(GaugeStatus.VERY_GOOD)
                .advice(List.of("현재 상태 설명", "관리 방법"))
                .build();
    }

    private static User user() {
        return User.builder()
                .id(1L)
                .nickname("은서")
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
