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
    private final MeasurementSocketService measurementSocketService;

    public void initialize(MeasurementSession measurementSession) {
        findOrCreate(measurementSession);
    }

    public void refreshCaptureCompletedByStatus(MeasurementSession measurementSession, MeasurementStatus status) {
        MeasurementAnalysisStatus analysisStatus = findOrCreate(measurementSession);

        if (status == MeasurementStatus.WAITING_FOR_PRESSURE
                || status == MeasurementStatus.READY_FOR_PRESSURE
                || status == MeasurementStatus.MEASURING_PRESSURE
                || status == MeasurementStatus.ANALYZING) {
            analysisStatus.completePhotoCapture();
        }

        if (status == MeasurementStatus.ANALYZING) {
            analysisStatus.completePressureCapture();
        }

        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void refreshPhotoAnalysisCompleted(MeasurementSession measurementSession) {
        MeasurementAnalysisStatus analysisStatus = findOrCreate(measurementSession);
        if (halluxValgusAnalysisRepository.existsByMeasurementSessionId(measurementSession.getId())
                && tinaPedisAnalysisRepository.existsByMeasurementSessionId(measurementSession.getId())) {
            analysisStatus.completePhotoCapture();
            analysisStatus.completePhotoAnalysis();
        }
        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void refreshPressureAnalysisCompleted(MeasurementSession measurementSession) {
        MeasurementAnalysisStatus analysisStatus = findOrCreate(measurementSession);
        dailyFootAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .filter(this::hasRequiredPressureAnalysis)
                .ifPresent(ignored -> {
                    analysisStatus.completePressureCapture();
                    analysisStatus.completePressureAnalysis();
                });
        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void refreshEnvironmentAnalysisCompleted(MeasurementSession measurementSession) {
        MeasurementAnalysisStatus analysisStatus = findOrCreate(measurementSession);
        dailyFootAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .filter(this::hasRequiredEnvironmentAnalysis)
                .ifPresent(ignored -> analysisStatus.completeEnvironmentAnalysis());
        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void refreshMetricReportCompleted(MeasurementSession measurementSession) {
        MeasurementAnalysisStatus analysisStatus = findOrCreate(measurementSession);
        reportRepository.findByMeasurementSessionId(measurementSession.getId())
                .filter(this::hasRequiredMetricResults)
                .ifPresent(ignored -> analysisStatus.completeMetricReport());
        completeMeasurementIfReady(measurementSession, analysisStatus, null);
    }

    public void completeMeasurementIfReady(MeasurementSession measurementSession, Integer measurementDurationSec) {
        completeMeasurementIfReady(measurementSession, findOrCreate(measurementSession), measurementDurationSec);
    }

    private MeasurementAnalysisStatus findOrCreate(MeasurementSession measurementSession) {
        return measurementAnalysisStatusRepository.findByMeasurementSessionId(measurementSession.getId())
                .orElseGet(() -> measurementAnalysisStatusRepository.save(
                        MeasurementAnalysisStatus.builder()
                                .measurementSession(measurementSession)
                                .build()
                ));
    }

    private boolean hasRequiredPressureAnalysis(DailyFootAnalysis analysis) {
        Long measurementSessionId = analysis.getMeasurementSession().getId();
        return hasText(analysis.getLeftPressureHeatmapImageUrl())
                && hasText(analysis.getRightPressureHeatmapImageUrl())
                && hasText(analysis.getLeftPlantarFootprintImageUrl())
                && hasText(analysis.getRightPlantarFootprintImageUrl())
                && hasText(analysis.getPlantarFootprintAnalysisText())
                && pressureSensorReadingRepository.existsByMeasurementSessionIdAndFootSide(
                        measurementSessionId, FootSide.LEFT)
                && pressureSensorReadingRepository.existsByMeasurementSessionIdAndFootSide(
                        measurementSessionId, FootSide.RIGHT);
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

        measurementSession.updateStatus(
                MeasurementStatus.COMPLETED,
                resolveMeasurementDurationSec(measurementSession, measurementDurationSec)
        );
        measurementSession.clearFailure();
        measurementSocketService.sendMeasurementStatusChanged(measurementSession);
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
