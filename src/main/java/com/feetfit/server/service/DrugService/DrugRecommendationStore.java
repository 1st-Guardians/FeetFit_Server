package com.feetfit.server.service.DrugService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.domain.DrugRecommendation;
import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DrugRecommendationStore {
    public record Prepared(Long reportId, String inputHash, String model, DrugRecommendationPolicy.Basis basis) {
    }

    private final DrugRecommendationRepository recommendations;
    private final ReportRepository reports;
    private final MeasurementSessionRepository sessions;
    private final HalluxValgusAnalysisRepository hallux;
    private final MetricAnalysisResultRepository metrics;
    private final DrugRecommendationPolicy policy;
    private final DrugCatalogMapper mapper;
    private final DrugRecommendationSummaryClient summaryClient;

    @Transactional
    public Prepared prepare(Long userId, Long measurementSessionId) {
        var session = sessions.findByIdForUpdate(measurementSessionId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEASUREMENT_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new GeneralException(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }
        if (session.getStatus() != MeasurementStatus.COMPLETED) {
            throw new GeneralException(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }
        var report = reports.findByMeasurementSessionId(measurementSessionId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REPORT_NOT_FOUND));
        Map<MetricType, Float> scores = metrics.findByReportId(report.getId()).stream()
                .filter(metric -> metric.getScore() != null)
                .collect(Collectors.toMap(MetricAnalysisResult::getMetricType, MetricAnalysisResult::getScore,
                        (first, second) -> second));
        var angles = hallux.findByMeasurementSessionId(measurementSessionId).orElse(null);
        var basis = policy.rank(scores.get(MetricType.SKIN_IRRITATION), scores.get(MetricType.ATHLETES_FOOT),
                angles == null ? null : angles.getLeftToeAngleDegree(),
                angles == null ? null : angles.getRightToeAngleDegree());
        String payload = mapper.serialize(basis);
        String summaryContract = "\n" + DrugRecommendationSummaryClient.SUMMARY_VERSION
                + (basis.allHealthy() ? "\n" + DrugRecommendationPolicy.HEALTHY_SUMMARY : "");
        String hash = hash(summaryClient.model() + "\n" + payload + summaryContract);
        var recommendation = recommendations.findById(report.getId())
                .orElseGet(() -> DrugRecommendation.builder().report(report).build());
        if (!hash.equals(recommendation.getInputHash())) {
            recommendation.prepare(payload, policy.fallbackSummary(basis), hash, summaryClient.model());
            recommendations.saveAndFlush(recommendation);
        }
        return new Prepared(report.getId(), hash, summaryClient.model(), basis);
    }

    @Transactional(readOnly = true)
    public Prepared readPrepared(Long reportId) {
        var value = recommendations.findById(reportId).orElseThrow();
        return new Prepared(reportId, value.getInputHash(), value.getModel(),
                mapper.deserialize(value.getPrioritiesPayload(), DrugRecommendationPolicy.Basis.class));
    }

    @Transactional
    public boolean claim(Prepared prepared) {
        Instant now = Instant.now();
        return recommendations.claim(prepared.reportId(), prepared.inputHash(), now, now.minus(Duration.ofMinutes(10))) == 1;
    }

    @Transactional
    public boolean complete(Prepared prepared, String summary) {
        return recommendations.complete(prepared.reportId(), prepared.inputHash(), summary, Instant.now()) == 1;
    }

    @Transactional
    public boolean fail(Prepared prepared) {
        return recommendations.fail(prepared.reportId(), prepared.inputHash()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared failPendingForSession(Long userId, Long measurementSessionId) {
        var report = reports.findByMeasurementSessionId(measurementSessionId).orElse(null);
        if (report == null || !report.getUser().getId().equals(userId)) {
            return null;
        }
        var saved = recommendations.findById(report.getId()).orElse(null);
        if (saved == null || recommendations.failPending(saved.getReportId(), saved.getInputHash()) == 0) {
            return null;
        }
        return new Prepared(report.getId(), saved.getInputHash(), saved.getModel(),
                mapper.deserialize(saved.getPrioritiesPayload(), DrugRecommendationPolicy.Basis.class));
    }

    @Transactional(readOnly = true)
    public List<Long> latestCompletedReportIds() {
        return reports.findLatestCompletedReportIds(MeasurementStatus.COMPLETED);
    }

    @Transactional
    public Prepared prepareReport(Long reportId) {
        var report = reports.findById(reportId).orElseThrow();
        return prepare(report.getUser().getId(), report.getMeasurementSession().getId());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
