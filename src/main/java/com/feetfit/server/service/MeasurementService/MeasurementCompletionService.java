package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.MeasurementAnalysisStatus;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.enums.FootSide;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class MeasurementCompletionService {

    private static final Set<MetricType> REQUIRED_METRIC_TYPES = EnumSet.of(
            MetricType.PRESSURE_BALANCE,
            MetricType.HALLUX_VALGUS,
            MetricType.ATHLETES_FOOT,
            MetricType.SKIN_IRRITATION,
            MetricType.FOOT_ENVIRONMENT
    );

    private final MeasurementAnalysisStatusRepository measurementAnalysisStatusRepository;
    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;
    private final PressureSensorReadingRepository pressureSensorReadingRepository;
    private final ReportRepository reportRepository;
    private final MetricAnalysisResultRepository metricAnalysisResultRepository;
    private final MeasurementSessionRepository measurementSessionRepository;
    private final MeasurementSocketService measurementSocketService;

    public void initialize(MeasurementSession measurementSession) {
        findOrCreate(measurementSession);
    }

    public void refreshCaptureCompletedByStatus(MeasurementSession measurementSession, MeasurementStatus status) {
        MeasurementAnalysisStatus analysisStatus = findOrCreateForUpdate(measurementSession);

        if (status == MeasurementStatus.WAITING_FOR_PRESSURE
                || status == MeasurementStatus.READY_FOR_PRESSURE
                || status == MeasurementStatus.MEASURING_PRESSURE
                || status == MeasurementStatus.ANALYZING) {
            refreshPhotoCaptureCompleted(measurementSession, analysisStatus);
        }

        if (status == MeasurementStatus.ANALYZING) {
            refreshPressureCaptureCompleted(measurementSession, analysisStatus);
        }

        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void refreshPhotoAnalysisCompleted(MeasurementSession measurementSession) {
        MeasurementAnalysisStatus analysisStatus = findOrCreateForUpdate(measurementSession);
        refreshPhotoCaptureCompleted(measurementSession, analysisStatus);
        if (hasRequiredPhotoAnalysis(measurementSession.getId())) {
            analysisStatus.completePhotoAnalysis();
        }
        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void refreshPressureAnalysisCompleted(MeasurementSession measurementSession) {
        MeasurementAnalysisStatus analysisStatus = findOrCreateForUpdate(measurementSession);
        refreshPressureCaptureCompleted(measurementSession, analysisStatus);
        dailyFootAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .filter(this::hasRequiredPressureAnalysis)
                .ifPresent(ignored -> analysisStatus.completePressureAnalysis());
        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void refreshEnvironmentAnalysisCompleted(MeasurementSession measurementSession) {
        MeasurementAnalysisStatus analysisStatus = findOrCreateForUpdate(measurementSession);
        dailyFootAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .filter(this::hasRequiredEnvironmentAnalysis)
                .ifPresent(ignored -> analysisStatus.completeEnvironmentAnalysis());
        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void refreshMetricReportCompleted(MeasurementSession measurementSession) {
        MeasurementAnalysisStatus analysisStatus = findOrCreateForUpdate(measurementSession);
        reportRepository.findByMeasurementSessionId(measurementSession.getId())
                .filter(this::hasRequiredMetricResults)
                .ifPresent(ignored -> analysisStatus.completeMetricReport());
        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void completeMeasurementIfReady(MeasurementSession measurementSession, Integer measurementDurationSec) {
        completeMeasurementIfReady(measurementSession, findOrCreateForUpdate(measurementSession), measurementDurationSec);
    }

    private MeasurementAnalysisStatus findOrCreate(MeasurementSession measurementSession) {
        return measurementAnalysisStatusRepository.findByMeasurementSessionId(measurementSession.getId())
                .orElseGet(() -> measurementAnalysisStatusRepository.save(
                        MeasurementAnalysisStatus.builder()
                                .measurementSession(measurementSession)
                                .build()
                ));
    }

    private MeasurementAnalysisStatus findOrCreateForUpdate(MeasurementSession measurementSession) {
        MeasurementSession lockedMeasurementSession = measurementSessionRepository
                .findByIdForUpdate(measurementSession.getId())
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));

        return measurementAnalysisStatusRepository.findByMeasurementSessionIdForUpdate(lockedMeasurementSession.getId())
                .orElseGet(() -> measurementAnalysisStatusRepository.saveAndFlush(
                        MeasurementAnalysisStatus.builder()
                                .measurementSession(lockedMeasurementSession)
                                .build()
                ));
    }

    private void refreshPhotoCaptureCompleted(
            MeasurementSession measurementSession,
            MeasurementAnalysisStatus analysisStatus) {
        if (hasRequiredPhotoCapture(measurementSession.getId())) {
            analysisStatus.completePhotoCapture();
        }
    }

    private void refreshPressureCaptureCompleted(
            MeasurementSession measurementSession,
            MeasurementAnalysisStatus analysisStatus) {
        if (hasRequiredPressureCapture(measurementSession.getId())) {
            analysisStatus.completePressureCapture();
        }
    }

    private boolean hasRequiredPhotoCapture(Long measurementSessionId) {
        boolean hasFootTopImage = tinaPedisAnalysisRepository.findByMeasurementSessionId(measurementSessionId)
                .map(analysis -> hasText(analysis.getOriginalFootImageUrl()))
                .orElse(false);
        boolean hasSoleImages = dailyFootAnalysisRepository.findByMeasurementSessionId(measurementSessionId)
                .map(analysis -> hasText(analysis.getLeftPlantarFootprintImageUrl())
                        && hasText(analysis.getRightPlantarFootprintImageUrl()))
                .orElse(false);
        return hasFootTopImage && hasSoleImages;
    }

    private boolean hasRequiredPhotoAnalysis(Long measurementSessionId) {
        return halluxValgusAnalysisRepository.existsByMeasurementSessionId(measurementSessionId)
                && tinaPedisAnalysisRepository.existsByMeasurementSessionId(measurementSessionId)
                && dailyFootAnalysisRepository.findByMeasurementSessionId(measurementSessionId)
                .filter(this::hasRequiredFootSizeAnalysis)
                .filter(this::hasRequiredPlantarFootprintAnalysis)
                .isPresent();
    }

    private boolean hasRequiredFootSizeAnalysis(DailyFootAnalysis analysis) {
        return analysis.getMeasuredLeftFootSizeMm() != null
                && analysis.getMeasuredRightFootSizeMm() != null
                && analysis.getLeftFootWidthMm() != null
                && analysis.getRightFootWidthMm() != null;
    }

    private boolean hasRequiredPlantarFootprintAnalysis(DailyFootAnalysis analysis) {
        return hasText(analysis.getLeftPlantarFootprintImageUrl())
                && hasText(analysis.getRightPlantarFootprintImageUrl())
                && hasText(analysis.getPlantarFootprintAnalysisText());
    }

    private boolean hasRequiredPressureCapture(Long measurementSessionId) {
        return hasTwelvePressureSensorValues(measurementSessionId, FootSide.LEFT)
                && hasTwelvePressureSensorValues(measurementSessionId, FootSide.RIGHT);
    }

    private boolean hasTwelvePressureSensorValues(Long measurementSessionId, FootSide footSide) {
        return pressureSensorReadingRepository
                .findByMeasurementSessionIdAndFootSide(measurementSessionId, footSide)
                .stream()
                .anyMatch(reading -> reading.getSensorValues().size() == 12);
    }

    private boolean hasRequiredPressureAnalysis(DailyFootAnalysis analysis) {
        return analysis.getLeftPressurePercent() != null
                && analysis.getRightPressurePercent() != null
                && hasText(analysis.getLeftPressureImageUrl())
                && hasText(analysis.getRightPressureImageUrl())
                && hasText(analysis.getLeftPressureHeatmapImageUrl())
                && hasText(analysis.getRightPressureHeatmapImageUrl())
                && hasRequiredPressureCapture(analysis.getMeasurementSession().getId());
    }

    private boolean hasRequiredEnvironmentAnalysis(DailyFootAnalysis analysis) {
        return analysis.getAvgTemperatureCelsius() != null
                && analysis.getAvgHumidityPercent() != null;
    }

    private boolean hasRequiredMetricResults(Report report) {
        List<MetricAnalysisResult> metricResults = metricAnalysisResultRepository.findByReportId(report.getId());
        Set<MetricType> savedMetricTypes = metricResults.stream()
                .map(MetricAnalysisResult::getMetricType)
                .collect(Collectors.toSet());
        return savedMetricTypes.containsAll(REQUIRED_METRIC_TYPES);
    }

    private void completeMeasurementIfReady(
            MeasurementSession measurementSession,
            MeasurementAnalysisStatus analysisStatus,
            Integer measurementDurationSec) {
        if (!analysisStatus.isReadyToComplete()
                || measurementSession.getStatus() == MeasurementStatus.COMPLETED
                || measurementSession.getStatus() == MeasurementStatus.FAILED) {
            return;
        }

        MeasurementSession lockedMeasurementSession = measurementSessionRepository
                .findByIdForUpdate(measurementSession.getId())
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));
        if (lockedMeasurementSession.getStatus() == MeasurementStatus.COMPLETED
                || lockedMeasurementSession.getStatus() == MeasurementStatus.FAILED) {
            return;
        }

        lockedMeasurementSession.updateStatus(
                MeasurementStatus.COMPLETED,
                resolveMeasurementDurationSec(lockedMeasurementSession, measurementDurationSec)
        );
        lockedMeasurementSession.clearFailure();
        measurementSocketService.sendMeasurementStatusChanged(lockedMeasurementSession);
    }

    private Integer resolveMeasurementDurationSec(MeasurementSession measurementSession, Integer requestedDurationSec) {
        if (requestedDurationSec != null && requestedDurationSec <= 0) {
            throw new MeasurementHandler(ErrorStatus._BAD_REQUEST);
        }

        if (requestedDurationSec != null && requestedDurationSec > 0) {
            return requestedDurationSec;
        }

        if (measurementSession.getMeasurementDurationSec() != null
                && measurementSession.getMeasurementDurationSec() > 0) {
            return measurementSession.getMeasurementDurationSec();
        }

        long measuredSeconds = Duration.between(measurementSession.getMeasuredAt(), LocalDateTime.now()).getSeconds();
        return (int) Math.max(1L, Math.min(measuredSeconds, Integer.MAX_VALUE));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
