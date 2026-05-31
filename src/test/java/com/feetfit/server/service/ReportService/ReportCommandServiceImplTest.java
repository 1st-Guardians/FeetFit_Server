package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.UserStretchingTodo;
import com.feetfit.server.domain.UserStretchingTodoAssignment;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.TinaPedisAnalysis;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.HealthType;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.DailyFootAnalysisRepository;
import com.feetfit.server.repository.MetricAnalysisResultRepository;
import com.feetfit.server.repository.ReportRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.repository.UserStretchingTodoAssignmentRepository;
import com.feetfit.server.repository.UserStretchingTodoRepository;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import com.feetfit.server.service.ImageService.ImageUploadService;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportCommandServiceImplTest {

    @Mock
    private HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;

    @Mock
    private MeasurementSessionRepository measurementSessionRepository;

    @Mock
    private TinaPedisAnalysisRepository tinaPedisAnalysisRepository;

    @Mock
    private DailyFootAnalysisRepository dailyFootAnalysisRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MetricAnalysisResultRepository metricAnalysisResultRepository;

    @Mock
    private UserStretchingTodoRepository userStretchingTodoRepository;

    @Mock
    private UserStretchingTodoAssignmentRepository userStretchingTodoAssignmentRepository;

    @Mock
    private ImageUploadService imageUploadService;

    @Mock
    private MeasurementSocketService measurementSocketService;

    @InjectMocks
    private ReportCommandServiceImpl reportCommandService;

    @Test
    void saveTinaPedisAnalysis_completedOwnMeasurement_savesAnalysis() {
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession(MeasurementStatus.TRANSFERRING)));
        given(tinaPedisAnalysisRepository.findByMeasurementSessionId(1L)).willReturn(Optional.empty());
        givenImageUploads();
        given(tinaPedisAnalysisRepository.save(any(TinaPedisAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        eq(1L),
                        any(LocalDateTime.class)
                ))
                .willReturn(Optional.empty());

        ReportResponseDTO.TinaPedisAnalysisResultDTO response =
                reportCommandService.saveTinaPedisAnalysis(1L, tinaPedisRequest(), anyImage(), anyImage());

        assertThat(response.getMeasurementSessionId()).isEqualTo(1L);
        assertThat(response.getFungalSuspicionSafetyScore()).isEqualTo(82);
        assertThat(response.getSkinReactionSafetyScore()).isEqualTo(76);
        assertThat(response.getTotalScore()).isEqualTo(80.2f);
        assertThat(response.getPreviousTotalScore()).isNull();
        assertThat(response.getTotalScoreDiff()).isNull();
        assertThat(response.getTotalScoreDescription()).isEqualTo("전반적으로 안전하지만 발 건조 관리가 필요합니다.");
        assertThat(response.getRecordedAt()).isNotNull();
    }

    @Test
    void saveTinaPedisAnalysis_existingAnalysis_updatesScoreAndFlushes() {
        TinaPedisAnalysis existingAnalysis = TinaPedisAnalysis.builder()
                .id(1L)
                .measurementSession(measurementSession(MeasurementStatus.TRANSFERRING))
                .fungalSuspicionSafetyScore(100)
                .skinReactionSafetyScore(100)
                .fungalSuspicionSafetyDescription("기존 진균 설명")
                .skinReactionSafetyDescription("기존 피부 설명")
                .totalScoreDescription("기존 종합 설명")
                .suspiciousAreaMapImageUrl("https://example.com/old-map.png")
                .originalFootImageUrl("https://example.com/old-original.png")
                .recordedAt(LocalDateTime.of(2026, 5, 19, 9, 0))
                .build();

        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession(MeasurementStatus.TRANSFERRING)));
        given(tinaPedisAnalysisRepository.findByMeasurementSessionId(1L)).willReturn(Optional.of(existingAnalysis));
        givenImageUploads();
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        eq(1L),
                        any(LocalDateTime.class)
                ))
                .willReturn(Optional.empty());

        ReportResponseDTO.TinaPedisAnalysisResultDTO response =
                reportCommandService.saveTinaPedisAnalysis(1L, tinaPedisRequest(93, 93), anyImage(), anyImage());

        assertThat(response.getFungalSuspicionSafetyScore()).isEqualTo(93);
        assertThat(response.getSkinReactionSafetyScore()).isEqualTo(93);
        assertThat(response.getTotalScore()).isEqualTo(93.0f);
        assertThat(response.getPreviousTotalScore()).isNull();
        assertThat(response.getTotalScoreDiff()).isNull();
        assertThat(existingAnalysis.getFungalSuspicionSafetyScore()).isEqualTo(93);
        assertThat(existingAnalysis.getSkinReactionSafetyScore()).isEqualTo(93);
        assertThat(existingAnalysis.getRecordedAt()).isNotEqualTo(LocalDateTime.of(2026, 5, 19, 9, 0));
    }

    @Test
    void saveTinaPedisAnalysis_notCompletedMeasurement_throwsMeasurementHandler() {
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession(MeasurementStatus.PENDING)));

        assertThatThrownBy(() -> reportCommandService.saveTinaPedisAnalysis(1L, tinaPedisRequest(), anyImage(), anyImage()))
                .isInstanceOf(MeasurementHandler.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveReport_matchesThreeTodosByWeightedLowScoreDistribution() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.TRANSFERRING);
        ReportRequestDTO.SaveReportDTO request = saveReportRequest();
        List<UserStretchingTodo> todos = List.of(
                stretchingTodo(1L, HealthType.FOOT_ENVIRONMENT, "발 건조 관리"),
                stretchingTodo(2L, HealthType.FOOT_ENVIRONMENT, "통풍 스트레칭"),
                stretchingTodo(3L, HealthType.FOOT_ENVIRONMENT, "발 환경 관리"),
                stretchingTodo(4L, HealthType.HALLUX_VALGUS, "발가락 벌리기"),
                stretchingTodo(5L, HealthType.POSTURE, "균형 스트레칭")
        );

        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession));
        given(reportRepository.findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).willReturn(Optional.empty());
        given(reportRepository.save(any(Report.class))).willAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 1L);
            return report;
        });
        given(metricAnalysisResultRepository.saveAll(any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userStretchingTodoRepository.findByHealthTypeInAndTodoDateGreaterThanEqualAndTodoDateLessThanOrderByIdAsc(
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).willReturn(todos);

        ReportResponseDTO.SaveReportResultDTO response = reportCommandService.saveReport(1L, request);

        assertThat(response.getTotalScore()).isEqualTo(73);
        assertThat(response.getMatchedTodoCount()).isEqualTo(3);
        assertThat(response.getMatchedHealthTypes()).containsExactly(HealthType.FOOT_ENVIRONMENT, HealthType.HALLUX_VALGUS);
        verify(userStretchingTodoAssignmentRepository).deleteByUserIdAndTodoDateBetween(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)
        );

        ArgumentCaptor<Iterable<UserStretchingTodoAssignment>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(userStretchingTodoAssignmentRepository).saveAll(captor.capture());
        List<UserStretchingTodoAssignment> assignments = StreamSupport.stream(captor.getValue().spliterator(), false)
                .toList();

        assertThat(assignments).hasSize(3);
        assertThat(assignments)
                .extracting(assignment -> assignment.getStretchingTodo().getHealthType())
                .containsExactly(HealthType.FOOT_ENVIRONMENT, HealthType.HALLUX_VALGUS, HealthType.FOOT_ENVIRONMENT);
    }

    @Test
    void saveTinaPedisAnalysis_whenBothRequiredAnalysesExist_keepsTransferringAndDoesNotSendSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.TRANSFERRING);
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession));
        given(tinaPedisAnalysisRepository.findByMeasurementSessionId(1L)).willReturn(Optional.empty());
        givenImageUploads();
        given(tinaPedisAnalysisRepository.save(any(TinaPedisAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        eq(1L),
                        any(LocalDateTime.class)
                ))
                .willReturn(Optional.empty());

        reportCommandService.saveTinaPedisAnalysis(1L, tinaPedisRequest(), anyImage(), anyImage());

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.TRANSFERRING);
        verify(measurementSocketService, never()).sendMeasurementCompleted(measurementSession);
    }

    @Test
    void saveTinaPedisAnalysis_whenMeasuring_marksMeasurementTransferringAndDoesNotSendSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.MEASURING);
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession));
        given(tinaPedisAnalysisRepository.findByMeasurementSessionId(1L)).willReturn(Optional.empty());
        givenImageUploads();
        given(tinaPedisAnalysisRepository.save(any(TinaPedisAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        eq(1L),
                        any(LocalDateTime.class)
                ))
                .willReturn(Optional.empty());

        reportCommandService.saveTinaPedisAnalysis(1L, tinaPedisRequest(), anyImage(), anyImage());

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.TRANSFERRING);
        verify(measurementSocketService, never()).sendMeasurementCompleted(measurementSession);
    }

    private static ReportRequestDTO.SaveTinaPedisAnalysisDTO tinaPedisRequest() {
        return tinaPedisRequest(82, 76);
    }

    private static ReportRequestDTO.SaveTinaPedisAnalysisDTO tinaPedisRequest(Integer fungalScore, Integer skinScore) {
        ReportRequestDTO.SaveTinaPedisAnalysisDTO request = new ReportRequestDTO.SaveTinaPedisAnalysisDTO();
        ReflectionTestUtils.setField(request, "measurementSessionId", 1L);
        ReflectionTestUtils.setField(request, "fungalSuspicionSafetyScore", fungalScore);
        ReflectionTestUtils.setField(request, "skinReactionSafetyScore", skinScore);
        ReflectionTestUtils.setField(request, "fungalSuspicionSafetyDescription", "발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.");
        ReflectionTestUtils.setField(request, "skinReactionSafetyDescription", "피부 발적과 자극 반응은 경미한 수준입니다.");
        ReflectionTestUtils.setField(request, "totalScoreDescription", "전반적으로 안전하지만 발 건조 관리가 필요합니다.");
        return request;
    }

    private void givenImageUploads() {
        given(imageUploadService.upload(eq("tina-pedis-map"), any(MultipartFile.class)))
                .willReturn(imageUploadResult("https://example.com/tina-pedis/map.png"));
        given(imageUploadService.upload(eq("tina-pedis-original"), any(MultipartFile.class)))
                .willReturn(imageUploadResult("https://example.com/tina-pedis/original.png"));
    }

    private static MultipartFile anyImage() {
        return org.springframework.mock.web.MockMultipartFile.class.cast(
                new org.springframework.mock.web.MockMultipartFile(
                        "image",
                        "foot.png",
                        "image/png",
                        "image".getBytes()
                )
        );
    }

    private static ImageResponseDTO.UploadImageResultDTO imageUploadResult(String imageUrl) {
        return ImageResponseDTO.UploadImageResultDTO.builder()
                .imageUrl(imageUrl)
                .build();
    }

    private static ReportRequestDTO.SaveReportDTO saveReportRequest() {
        ReportRequestDTO.SaveReportDTO request = new ReportRequestDTO.SaveReportDTO();
        ReflectionTestUtils.setField(request, "measurementSessionId", 1L);
        ReflectionTestUtils.setField(request, "metricAnalysisResults", List.of(
                metricAnalysisResult(MetricType.PRESSURE_BALANCE, 75.0f),
                metricAnalysisResult(MetricType.FOOT_ENVIRONMENT, 50.0f),
                metricAnalysisResult(MetricType.ATHLETES_FOOT, 90.0f),
                metricAnalysisResult(MetricType.HALLUX_VALGUS, 70.0f),
                metricAnalysisResult(MetricType.FOOT_ODOR, 80.0f)
        ));
        return request;
    }

    private static ReportRequestDTO.SaveMetricAnalysisResultDTO metricAnalysisResult(MetricType metricType, Float score) {
        ReportRequestDTO.SaveMetricAnalysisResultDTO request = new ReportRequestDTO.SaveMetricAnalysisResultDTO();
        ReflectionTestUtils.setField(request, "metricType", metricType);
        ReflectionTestUtils.setField(request, "score", score);
        ReflectionTestUtils.setField(request, "status", score >= 80.0f ? GaugeStatus.VERY_GOOD : GaugeStatus.ATTENTION_NEEDED);
        ReflectionTestUtils.setField(request, "advice", List.of("첫 번째 어드바이스", "두 번째 어드바이스"));
        return request;
    }

    private static UserStretchingTodo stretchingTodo(Long id, HealthType healthType, String title) {
        return UserStretchingTodo.builder()
                .id(id)
                .title(title)
                .healthType(healthType)
                .youtubeUrl("https://www.youtube.com/watch?v=stretching" + id)
                .todoDate(LocalDateTime.of(2026, 5, 20, 9, 0))
                .build();
    }

    private static MeasurementSession measurementSession(MeasurementStatus status) {
        return MeasurementSession.builder()
                .id(1L)
                .user(user())
                .status(status)
                .measuredAt(LocalDateTime.of(2026, 5, 20, 8, 0))
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
