package com.feetfit.server.service.ReportService;

import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.ShoeService.ShoeRecommendationAiClient;
import com.feetfit.server.web.dto.report.FootTypeTextAiDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FootTypeTextAutomationOrchestratorTest {

    @Mock FootTypeTextAutomationContextService contextService;
    @Mock ShoeRecommendationAiClient aiClient;
    @Mock TokenProvider tokenProvider;

    private FootTypeTextAutomationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new FootTypeTextAutomationOrchestrator(
                contextService, aiClient, tokenProvider);
    }

    @Test
    void completedSessionCallsAiThenSavesSameFactsOnly() {
        FootTypeTextAiDTO.Request request = request();
        given(aiClient.isEnabled()).willReturn(true);
        given(contextService.loadPendingContext(7L, 21L))
                .willReturn(Optional.of(request));
        given(tokenProvider.createAccessToken(7L)).willReturn("service-jwt");
        given(aiClient.requestFootTypeText(request, "service-jwt"))
                .willReturn(response("오른발에 압력이 조금 더 실리는 편이에요."));
        given(contextService.saveIfCurrentAndAbsent(
                7L, 21L, request.factsHash(),
                "오른발에 압력이 조금 더 실리는 편이에요."))
                .willReturn(true);

        orchestrator.generateIfMissing(7L, 21L);

        verify(contextService).saveIfCurrentAndAbsent(
                7L, 21L, request.factsHash(),
                "오른발에 압력이 조금 더 실리는 편이에요.");
    }

    @Test
    void existingTextSkipsTokenAndAi() {
        given(aiClient.isEnabled()).willReturn(true);
        given(contextService.loadPendingContext(7L, 21L))
                .willReturn(Optional.empty());

        orchestrator.generateIfMissing(7L, 21L);

        verify(tokenProvider, never()).createAccessToken(7L);
        verify(aiClient, never()).requestFootTypeText(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void wrongHashNeverSavesAndNeverEscapesAfterCommitWorker() {
        FootTypeTextAiDTO.Request request = request();
        given(aiClient.isEnabled()).willReturn(true);
        given(contextService.loadPendingContext(7L, 21L))
                .willReturn(Optional.of(request));
        given(tokenProvider.createAccessToken(7L)).willReturn("service-jwt");
        given(aiClient.requestFootTypeText(request, "service-jwt"))
                .willReturn(new FootTypeTextAiDTO.Response(
                        21L,
                        "0".repeat(64),
                        "오른발에 압력이 조금 더 실리는 편이에요.",
                        "PRESSURE_RIGHT_DOMINANT",
                        "OPENAI"));

        assertThatCode(() -> orchestrator.generateIfMissing(7L, 21L))
                .doesNotThrowAnyException();
        verify(contextService, never()).saveIfCurrentAndAbsent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void prohibitedOpeningNeverSavesEvenWhenSessionAndFactsMatch() {
        FootTypeTextAiDTO.Request request = request();
        given(aiClient.isEnabled()).willReturn(true);
        given(contextService.loadPendingContext(7L, 21L))
                .willReturn(Optional.of(request));
        given(tokenProvider.createAccessToken(7L)).willReturn("service-jwt");
        given(aiClient.requestFootTypeText(request, "service-jwt"))
                .willReturn(new FootTypeTextAiDTO.Response(
                        21L,
                        request.factsHash(),
                        "  이번 측정에서는 오른발에 압력이 더 실려요.",
                        "PRESSURE_RIGHT_DOMINANT",
                        "OPENAI"));

        assertThatCode(() -> orchestrator.generateIfMissing(7L, 21L))
                .doesNotThrowAnyException();
        verify(contextService, never()).saveIfCurrentAndAbsent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void aiFailureDoesNotChangeMeasurementOrRecommendationState() {
        FootTypeTextAiDTO.Request request = request();
        given(aiClient.isEnabled()).willReturn(true);
        given(contextService.loadPendingContext(7L, 21L))
                .willReturn(Optional.of(request));
        given(tokenProvider.createAccessToken(7L)).willReturn("service-jwt");
        given(aiClient.requestFootTypeText(request, "service-jwt"))
                .willThrow(new IllegalStateException("AI unavailable"));

        assertThatCode(() -> orchestrator.generateIfMissing(7L, 21L))
                .doesNotThrowAnyException();
        verify(contextService, never()).saveIfCurrentAndAbsent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private static FootTypeTextAiDTO.Request request() {
        return new FootTypeTextAiDTO.Request(
                21L,
                MeasurementStatus.COMPLETED,
                "a".repeat(64),
                new FootTypeTextAiDTO.Analysis(
                        253.0f, 248.0f, 85.0f, 70.0f, 46.0f, 54.0f,
                        "왼발 뒤꿈치와 오른발 앞꿈치에 압력이 집중되어 있습니다."
                )
        );
    }

    private static FootTypeTextAiDTO.Response response(String typeText) {
        return new FootTypeTextAiDTO.Response(
                21L,
                "a".repeat(64),
                typeText,
                "PRESSURE_RIGHT_DOMINANT",
                "OPENAI"
        );
    }
}
