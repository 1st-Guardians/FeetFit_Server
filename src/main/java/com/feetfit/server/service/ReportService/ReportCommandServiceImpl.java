package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ReportConverter;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.*;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ReportCommandServiceImpl implements ReportCommandService {

    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    private final MeasurementSessionRepository measurementSessionRepository;
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final MetricAnalysisResultRepository metricAnalysisResultRepository;

    @Override
    public ReportResponseDTO.SaveHalluxValgusResultDTO saveHalluxValgusAnalysis(
            Long userId, ReportRequestDTO.SaveHalluxValgusDTO request) {

        MeasurementSession measurementSession = measurementSessionRepository
                .findById(request.getMeasurementSessionId())
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));

        // 본인 측정 세션인지 검증
        if (!measurementSession.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }

        // COMPLETED 상태인지 검증
        if (!measurementSession.getStatus().equals(MeasurementStatus.COMPLETED)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }

        // 오늘 날짜에 이미 저장된 데이터 있는지 조회
        HalluxValgusAnalysis saved = halluxValgusAnalysisRepository
                .findByUserIdAndDate(userId, LocalDateTime.now())
                .map(existing -> {
                    // 있으면 UPDATE
                    existing.updateHalluxValgusAnalysis(
                            request.getImageUrl(),
                            request.getLeftToeAngleDegree(), request.getLeftRiskLevel(), request.getLeftAnalysisText(),
                            request.getRightToeAngleDegree(), request.getRightRiskLevel(), request.getRightAnalysisText(),
                            request.getRiskScore(), request.getScoreAnalysisText()
                    );
                    return existing;
                })
                .orElseGet(() ->
                        // 없으면 INSERT
                        halluxValgusAnalysisRepository.save(
                                ReportConverter.toHalluxValgusAnalysis(measurementSession, request)
                        )
                );

        return ReportConverter.toSaveHalluxValgusResultDTO(saved);
    }

    @Override
    public ReportResponseDTO.TinaPedisAnalysisResultDTO saveTinaPedisAnalysis(
            Long userId,
            ReportRequestDTO.SaveTinaPedisAnalysisDTO request
    ) {
        MeasurementSession measurementSession = getValidatedCompletedMeasurementSession(
                userId,
                request.getMeasurementSessionId()
        );

        TinaPedisAnalysis saved = tinaPedisAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .map(existing -> {
                    existing.updateTinaPedisAnalysis(
                            request.getFungalSuspicionSafetyScore(),
                            request.getSkinReactionSafetyScore(),
                            request.getFungalSuspicionSafetyDescription(),
                            request.getSkinReactionSafetyDescription(),
                            request.getTotalScoreDescription(),
                            request.getSuspiciousAreaMapImageUrl(),
                            request.getOriginalFootImageUrl(),
                            request.getRecordedAt()
                    );
                    return existing;
                })
                .orElseGet(() -> tinaPedisAnalysisRepository.save(
                        ReportConverter.toTinaPedisAnalysis(measurementSession, request)
                ));

        tinaPedisAnalysisRepository.flush();

        TinaPedisAnalysis previousAnalysis = tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        userId,
                        saved.getRecordedAt().toLocalDate().atStartOfDay()
                )
                .orElse(null);

        return ReportConverter.toTinaPedisAnalysisResultDTO(saved, previousAnalysis);
    }

    private MeasurementSession getValidatedCompletedMeasurementSession(Long userId, Long measurementSessionId) {
        MeasurementSession measurementSession = measurementSessionRepository
                .findById(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));

        if (!measurementSession.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }

        if (!measurementSession.getStatus().equals(MeasurementStatus.COMPLETED)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }

        return measurementSession;
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO saveDailyFootAnalysis(
            Long userId, ReportRequestDTO.SaveDailyFootAnalysisDTO request) {

        MeasurementSession measurementSession = getValidatedCompletedMeasurementSession(
                userId, request.getMeasurementSessionId()
        );

        DailyFootAnalysis saved = dailyFootAnalysisRepository
                .findByMeasurementSessionId(measurementSession.getId())
                .map(existing -> {
                    existing.update(
                            request.getConditionLevel(),
                            request.getConditionComments(),
                            request.getBalanceScore(),
                            request.getBalanceComment(),
                            request.getLeftPressurePercent(),
                            request.getRightPressurePercent(),
                            request.getLeftPressureImageUrl(),
                            request.getRightPressureImageUrl(),
                            request.getMeasuredLeftFootSizeMm(),
                            request.getMeasuredRightFootSizeMm(),
                            request.getLeftFootWidthMm(),
                            request.getRightFootWidthMm(),
                            request.getFootOdourPpm(),
                            request.getFootOdourComment(),
                            request.getAvgTemperatureCelsius(),
                            request.getAvgHumidityPercent(),
                            request.getCareTips(),
                            request.getTypeText()
                    );
                    return existing;
                })
                .orElseGet(() -> dailyFootAnalysisRepository.save(
                        ReportConverter.toDailyFootAnalysis(measurementSession, request)
                ));

        // 유저 발 사이즈 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        // 이전 측정 데이터 조회
        DailyFootAnalysis previousAnalysis = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, saved.getCreatedAt().toLocalDate().atStartOfDay()
                )
                .orElse(null);

        return ReportConverter.toDailyFootAnalysisResultDTO(saved, user.getFootSize(), previousAnalysis);
    }

    @Override
    public ReportResponseDTO.SaveReportResultDTO saveReport(
            Long userId, ReportRequestDTO.SaveReportDTO request) {

        MeasurementSession measurementSession = getValidatedCompletedMeasurementSession(
                userId, request.getMeasurementSessionId());

        int totalScore = Math.round((float) request.getMetricAnalysisResults().stream()
                .mapToDouble(dto -> dto.getScore())
                .average()
                .orElse(0.0));

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();

        Report report = reportRepository
                .findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
                        userId, startOfDay, endOfDay)
                .map(existing -> {
                    existing.updateTotalScore(totalScore);
                    existing.updateReportDate(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> reportRepository.save(
                        Report.builder()
                                .measurementSession(measurementSession)
                                .user(measurementSession.getUser())
                                .reportDate(LocalDateTime.now())
                                .totalScore(totalScore)
                                .build()
                ));

        // 기존 MetricAnalysisResult 먼저 삭제
        metricAnalysisResultRepository.deleteByReportId(report.getId());
        metricAnalysisResultRepository.flush();  // ← DB에 즉시 반영

        // 새 MetricAnalysisResult 저장
        List<MetricAnalysisResult> metricAnalysisResults = request.getMetricAnalysisResults().stream()
                .map(dto -> ReportConverter.toMetricAnalysisResult(report, dto))
                .collect(Collectors.toList());

        metricAnalysisResultRepository.saveAll(metricAnalysisResults);

        // 연관관계 동기화
        report.getMetricAnalysisResults().clear();
        report.getMetricAnalysisResults().addAll(metricAnalysisResults);

        return ReportConverter.toSaveReportResultDTO(report);
    }
}
