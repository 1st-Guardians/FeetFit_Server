package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.TinaPedisAnalysis;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReportCommandServiceImplTest {

    @Mock
    private HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;

    @Mock
    private MeasurementSessionRepository measurementSessionRepository;

    @Mock
    private TinaPedisAnalysisRepository tinaPedisAnalysisRepository;

    @InjectMocks
    private ReportCommandServiceImpl reportCommandService;

    @Test
    void saveTinaPedisAnalysis_completedOwnMeasurement_savesAnalysis() {
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession(MeasurementStatus.COMPLETED)));
        given(tinaPedisAnalysisRepository.findByMeasurementSessionId(1L)).willReturn(Optional.empty());
        given(tinaPedisAnalysisRepository.save(any(TinaPedisAnalysis.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0)
                ))
                .willReturn(Optional.empty());

        ReportResponseDTO.TinaPedisAnalysisResultDTO response =
                reportCommandService.saveTinaPedisAnalysis(1L, tinaPedisRequest());

        assertThat(response.getMeasurementSessionId()).isEqualTo(1L);
        assertThat(response.getFungalSuspicionSafetyScore()).isEqualTo(82);
        assertThat(response.getSkinReactionSafetyScore()).isEqualTo(76);
        assertThat(response.getTotalScore()).isEqualTo(80.2f);
        assertThat(response.getPreviousTotalScore()).isNull();
        assertThat(response.getTotalScoreDiff()).isNull();
        assertThat(response.getTotalScoreDescription()).isEqualTo("전반적으로 안전하지만 발 건조 관리가 필요합니다.");
    }

    @Test
    void saveTinaPedisAnalysis_existingAnalysis_updatesScoreAndFlushes() {
        TinaPedisAnalysis existingAnalysis = TinaPedisAnalysis.builder()
                .id(1L)
                .measurementSession(measurementSession(MeasurementStatus.COMPLETED))
                .fungalSuspicionSafetyScore(100)
                .skinReactionSafetyScore(100)
                .fungalSuspicionSafetyDescription("기존 진균 설명")
                .skinReactionSafetyDescription("기존 피부 설명")
                .totalScoreDescription("기존 종합 설명")
                .suspiciousAreaMapImageUrl("https://example.com/old-map.png")
                .originalFootImageUrl("https://example.com/old-original.png")
                .recordedAt(LocalDateTime.of(2026, 5, 19, 9, 0))
                .build();

        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession(MeasurementStatus.COMPLETED)));
        given(tinaPedisAnalysisRepository.findByMeasurementSessionId(1L)).willReturn(Optional.of(existingAnalysis));
        given(tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        1L,
                        LocalDateTime.of(2026, 5, 20, 0, 0)
                ))
                .willReturn(Optional.empty());

        ReportResponseDTO.TinaPedisAnalysisResultDTO response =
                reportCommandService.saveTinaPedisAnalysis(1L, tinaPedisRequest(93, 93));

        assertThat(response.getFungalSuspicionSafetyScore()).isEqualTo(93);
        assertThat(response.getSkinReactionSafetyScore()).isEqualTo(93);
        assertThat(response.getTotalScore()).isEqualTo(93.0f);
        assertThat(response.getPreviousTotalScore()).isNull();
        assertThat(response.getTotalScoreDiff()).isNull();
        assertThat(existingAnalysis.getFungalSuspicionSafetyScore()).isEqualTo(93);
        assertThat(existingAnalysis.getSkinReactionSafetyScore()).isEqualTo(93);
    }

    @Test
    void saveTinaPedisAnalysis_notCompletedMeasurement_throwsMeasurementHandler() {
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession(MeasurementStatus.PENDING)));

        assertThatThrownBy(() -> reportCommandService.saveTinaPedisAnalysis(1L, tinaPedisRequest()))
                .isInstanceOf(MeasurementHandler.class);
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
        ReflectionTestUtils.setField(request, "suspiciousAreaMapImageUrl", "https://example.com/tina-pedis/map.png");
        ReflectionTestUtils.setField(request, "originalFootImageUrl", "https://example.com/tina-pedis/original.png");
        ReflectionTestUtils.setField(request, "recordedAt", LocalDateTime.of(2026, 5, 20, 9, 0));
        return request;
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
