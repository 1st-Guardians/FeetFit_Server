package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.DailyFootAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.web.dto.report.FootTypeTextAiDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FootTypeTextAutomationContextServiceTest {

    @Mock MeasurementSessionRepository measurementSessionRepository;
    @Mock DailyFootAnalysisRepository dailyFootAnalysisRepository;

    private FootTypeTextAutomationContextService service;

    @BeforeEach
    void setUp() {
        service = new FootTypeTextAutomationContextService(
                measurementSessionRepository, dailyFootAnalysisRepository);
    }

    @Test
    void completedOwnedSessionBuildsExactPendingFactsWithoutCareTipsOrExistingText() {
        MeasurementSession session = session(MeasurementStatus.COMPLETED, 7L);
        DailyFootAnalysis analysis = analysis(session, null);
        given(measurementSessionRepository.findById(21L)).willReturn(Optional.of(session));
        given(dailyFootAnalysisRepository.findByMeasurementSessionId(21L))
                .willReturn(Optional.of(analysis));

        FootTypeTextAiDTO.Request request =
                service.loadPendingContext(7L, 21L).orElseThrow();

        assertThat(request.measurementSessionId()).isEqualTo(21L);
        assertThat(request.measurementStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        assertThat(request.factsHash()).matches("[0-9a-f]{64}");
        assertThat(request.analysis().leftPressurePercent()).isEqualTo(46.0f);
        assertThat(request.analysis().rightPressurePercent()).isEqualTo(54.0f);
        assertThat(request.analysis().plantarFootprintAnalysisText())
                .isEqualTo("왼발 뒤꿈치와 오른발 앞꿈치에 압력이 집중되어 있습니다.");
    }

    @Test
    void existingTypeTextMakesAutomaticGenerationIdempotentlySkip() {
        MeasurementSession session = session(MeasurementStatus.COMPLETED, 7L);
        given(measurementSessionRepository.findById(21L)).willReturn(Optional.of(session));
        given(dailyFootAnalysisRepository.findByMeasurementSessionId(21L))
                .willReturn(Optional.of(analysis(session, "이미 생성됨")));

        assertThat(service.loadPendingContext(7L, 21L)).isEmpty();
    }

    @Test
    void saveUpdatesOnlyTypeTextAndPreservesCareTips() {
        MeasurementSession session = session(MeasurementStatus.COMPLETED, 7L);
        DailyFootAnalysis analysis = analysis(session, null);
        String factsHash = FootTypeTextAutomationContextService.factsHash(
                new FootTypeTextAiDTO.Analysis(
                        253.0f, 248.0f, 85.0f, 70.0f, 46.0f, 54.0f,
                        "왼발 뒤꿈치와 오른발 앞꿈치에 압력이 집중되어 있습니다."
                ));
        given(measurementSessionRepository.findByIdForUpdate(21L))
                .willReturn(Optional.of(session));
        given(dailyFootAnalysisRepository.findByMeasurementSessionId(21L))
                .willReturn(Optional.of(analysis));

        boolean saved = service.saveIfCurrentAndAbsent(
                7L, 21L, factsHash, "오른발에 압력이 조금 더 실리는 편이에요.");

        assertThat(saved).isTrue();
        assertThat(analysis.getTypeText())
                .isEqualTo("오른발에 압력이 조금 더 실리는 편이에요.");
        assertThat(analysis.getCareTips()).containsExactly("기존 팁 1", "기존 팁 2", "기존 팁 3");
    }

    @Test
    void staleFactsHashCannotOverwriteSession() {
        MeasurementSession session = session(MeasurementStatus.COMPLETED, 7L);
        DailyFootAnalysis analysis = analysis(session, null);
        given(measurementSessionRepository.findByIdForUpdate(21L))
                .willReturn(Optional.of(session));
        given(dailyFootAnalysisRepository.findByMeasurementSessionId(21L))
                .willReturn(Optional.of(analysis));

        assertThatThrownBy(() -> service.saveIfCurrentAndAbsent(
                7L, 21L, "0".repeat(64), "문구"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("facts changed");
        assertThat(analysis.getTypeText()).isNull();
    }

    @Test
    void otherUserOrRunningSessionIsRejected() {
        given(measurementSessionRepository.findById(21L))
                .willReturn(Optional.of(session(MeasurementStatus.COMPLETED, 8L)));
        assertThatThrownBy(() -> service.loadPendingContext(7L, 21L))
                .isInstanceOf(MeasurementHandler.class);

        given(measurementSessionRepository.findById(22L))
                .willReturn(Optional.of(session(MeasurementStatus.ANALYZING, 7L)));
        assertThatThrownBy(() -> service.loadPendingContext(7L, 22L))
                .isInstanceOf(MeasurementHandler.class);
    }

    private static MeasurementSession session(MeasurementStatus status, Long userId) {
        return MeasurementSession.builder()
                .id(21L)
                .user(User.builder().id(userId).build())
                .status(status)
                .build();
    }

    private static DailyFootAnalysis analysis(
            MeasurementSession session, String typeText) {
        return DailyFootAnalysis.builder()
                .id(14L)
                .measurementSession(session)
                .measuredLeftFootSizeMm(253.0f)
                .measuredRightFootSizeMm(248.0f)
                .leftFootWidthMm(85.0f)
                .rightFootWidthMm(70.0f)
                .leftPressurePercent(46.0f)
                .rightPressurePercent(54.0f)
                .plantarFootprintAnalysisText(
                        "왼발 뒤꿈치와 오른발 앞꿈치에 압력이 집중되어 있습니다.")
                .careTips(List.of("기존 팁 1", "기존 팁 2", "기존 팁 3"))
                .typeText(typeText)
                .build();
    }
}
