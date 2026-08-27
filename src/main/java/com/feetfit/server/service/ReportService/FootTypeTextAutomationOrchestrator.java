package com.feetfit.server.service.ReportService;

import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.ShoeService.ShoeRecommendationAiClient;
import com.feetfit.server.web.dto.report.FootTypeTextAiDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FootTypeTextAutomationOrchestrator {

    private final FootTypeTextAutomationContextService contextService;
    private final ShoeRecommendationAiClient aiClient;
    private final TokenProvider tokenProvider;

    public void generateIfMissing(Long userId, Long measurementSessionId) {
        if (!aiClient.isEnabled()) {
            log.info(
                    "Automatic foot type text is disabled. measurementSessionId={}",
                    measurementSessionId);
            return;
        }

        try {
            Optional<FootTypeTextAiDTO.Request> pending =
                    contextService.loadPendingContext(userId, measurementSessionId);
            if (pending.isEmpty()) {
                log.info(
                        "Automatic foot type text already exists. measurementSessionId={}",
                        measurementSessionId);
                return;
            }

            FootTypeTextAiDTO.Request request = pending.get();
            String accessToken = tokenProvider.createAccessToken(userId);
            FootTypeTextAiDTO.Response response =
                    aiClient.requestFootTypeText(request, accessToken);
            validateResponse(request, response);

            boolean saved = contextService.saveIfCurrentAndAbsent(
                    userId,
                    measurementSessionId,
                    request.factsHash(),
                    response.typeText()
            );
            log.info(
                    "Automatic foot type text finished. measurementSessionId={}, saved={}, source={}, evidenceId={}",
                    measurementSessionId,
                    saved,
                    response.source(),
                    response.evidenceId());
        } catch (Exception exception) {
            // This path runs after the measurement transaction committed. Text
            // generation failure must not change COMPLETED or shoe recommendation state.
            log.error(
                    "Automatic foot type text failed. measurementSessionId={}, errorType={}",
                    measurementSessionId,
                    exception.getClass().getSimpleName(),
                    exception);
        }
    }

    private static void validateResponse(
            FootTypeTextAiDTO.Request request,
            FootTypeTextAiDTO.Response response) {
        if (!request.measurementSessionId().equals(response.measurementSessionId())) {
            throw new IllegalStateException(
                    "Feetfit_AI returned a different measurementSessionId");
        }
        if (response.factsHash() == null || !MessageDigest.isEqual(
                request.factsHash().getBytes(StandardCharsets.UTF_8),
                response.factsHash().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Feetfit_AI returned a different factsHash");
        }
        if (!StringUtils.hasText(response.typeText())
                || !StringUtils.hasText(response.evidenceId())
                || !StringUtils.hasText(response.source())) {
            throw new IllegalStateException("Feetfit_AI returned an incomplete foot type response");
        }
        if (response.typeText().strip().startsWith("이번 측정에서는")) {
            throw new IllegalStateException("Feetfit_AI returned the prohibited opening phrase");
        }
    }
}
