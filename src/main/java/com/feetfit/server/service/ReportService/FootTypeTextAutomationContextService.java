package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.DailyFootAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.web.dto.report.FootTypeTextAiDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FootTypeTextAutomationContextService {

    private static final int TYPE_TEXT_MAX_LENGTH = 500;

    private final MeasurementSessionRepository measurementSessionRepository;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;

    @Transactional(readOnly = true)
    public Optional<FootTypeTextAiDTO.Request> loadPendingContext(
            Long userId, Long measurementSessionId) {
        MeasurementSession session = measurementSessionRepository.findById(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));
        validateOwnedCompletedSession(session, userId);

        DailyFootAnalysis analysis = dailyFootAnalysisRepository
                .findByMeasurementSessionId(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(
                        ErrorStatus.MEASUREMENT_ANALYSIS_NOT_READY));
        if (StringUtils.hasText(analysis.getTypeText())) {
            return Optional.empty();
        }

        FootTypeTextAiDTO.Analysis facts = toAnalysis(analysis);
        return Optional.of(new FootTypeTextAiDTO.Request(
                measurementSessionId,
                session.getStatus(),
                factsHash(facts),
                facts
        ));
    }

    @Transactional
    public boolean saveIfCurrentAndAbsent(
            Long userId,
            Long measurementSessionId,
            String expectedFactsHash,
            String typeText) {
        if (!StringUtils.hasText(typeText) || typeText.length() > TYPE_TEXT_MAX_LENGTH) {
            throw new IllegalArgumentException("Generated foot type text is blank or too long");
        }

        MeasurementSession session = measurementSessionRepository
                .findByIdForUpdate(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));
        validateOwnedCompletedSession(session, userId);
        DailyFootAnalysis analysis = dailyFootAnalysisRepository
                .findByMeasurementSessionId(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(
                        ErrorStatus.MEASUREMENT_ANALYSIS_NOT_READY));
        if (StringUtils.hasText(analysis.getTypeText())) {
            return false;
        }
        if (!MessageDigest.isEqual(
                factsHash(toAnalysis(analysis)).getBytes(StandardCharsets.UTF_8),
                expectedFactsHash.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException(
                    "Foot analysis facts changed while typeText was being generated");
        }

        analysis.updateTypeText(typeText.strip());
        return true;
    }

    static String factsHash(FootTypeTextAiDTO.Analysis analysis) {
        String canonical = Stream.of(
                        analysis.measuredLeftFootSizeMm(),
                        analysis.measuredRightFootSizeMm(),
                        analysis.leftFootWidthMm(),
                        analysis.rightFootWidthMm(),
                        analysis.leftPressurePercent(),
                        analysis.rightPressurePercent(),
                        analysis.plantarFootprintAnalysisText()
                )
                .map(value -> value == null ? "<null>" : value.toString())
                .collect(Collectors.joining("\u001f"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static FootTypeTextAiDTO.Analysis toAnalysis(DailyFootAnalysis analysis) {
        return new FootTypeTextAiDTO.Analysis(
                analysis.getMeasuredLeftFootSizeMm(),
                analysis.getMeasuredRightFootSizeMm(),
                analysis.getLeftFootWidthMm(),
                analysis.getRightFootWidthMm(),
                analysis.getLeftPressurePercent(),
                analysis.getRightPressurePercent(),
                analysis.getPlantarFootprintAnalysisText()
        );
    }

    private static void validateOwnedCompletedSession(
            MeasurementSession session, Long userId) {
        if (!session.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }
        if (session.getStatus() != MeasurementStatus.COMPLETED) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }
    }
}
