package com.feetfit.server.service.DrugService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.DrugRecommendationRepository;
import com.feetfit.server.repository.ReportRepository;
import com.feetfit.server.repository.drug.DrugProductRepository;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DrugRecommendationQueryService {
    private final ReportRepository reports;
    private final DrugRecommendationRepository recommendations;
    private final DrugProductRepository products;
    private final DrugCatalogMapper mapper;
    private final DrugRecommendationStore store;
    private final DrugRecommendationGenerationService generation;

    @Transactional(readOnly = true)
    public DrugResponseDTO.DrugRecommendationResultDTO get(Long userId, String categoryId, String query, int page, int size) {
        var report = findLatestCompletedReport(userId).orElse(null);
        var saved = report == null ? null : recommendations.findById(report.getId()).orElse(null);
        var basis = saved == null ? null : mapper.deserialize(saved.getPrioritiesPayload(), DrugRecommendationPolicy.Basis.class);
        List<DrugCategory> order = basis == null
                ? DrugRecommendationPolicy.DEFAULT_ORDER
                : basis.priorities().stream().map(DrugRecommendationPolicy.Priority::category).toList();
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (keyword.length() > 50) {
            keyword = keyword.substring(0, 50);
        }
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        DrugCategory category = categoryId == null || categoryId.isBlank() ? null : DrugCategory.fromIdOrDefault(categoryId);
        var result = products.recommend(order.get(0), order.get(1), category, keyword, pattern, PageRequest.of(page, size));
        if (!result.isEmpty()) {
            // Load category memberships in one query, without collection-fetch pagination or N+1 reads.
            products.findForImport(result.getContent().stream().map(product -> product.getId()).toList());
        }
        List<DrugResponseDTO.DrugPriorityDTO> priorities = new ArrayList<>();
        if (basis != null) {
            for (int i = 0; i < basis.priorities().size(); i++) {
                var item = basis.priorities().get(i);
                priorities.add(DrugResponseDTO.DrugPriorityDTO.builder()
                        .rank(i + 1).category(item.category().getId()).categoryLabel(item.category().getLabel())
                        .metricType(item.metricType().name()).safetyScore(item.safetyScore()).priorityScore(item.priorityScore())
                        .leftToeAngleDegree(item.leftToeAngleDegree()).rightToeAngleDegree(item.rightToeAngleDegree())
                        .hvaLevel(item.hvaLevel()).reason(item.reason()).build());
            }
        }
        return DrugResponseDTO.DrugRecommendationResultDTO.builder()
                .hasMeasurement(report != null).reportId(report == null ? null : report.getId())
                .measurementSessionId(report == null ? null : report.getMeasurementSession().getId())
                .summary(saved == null ? null : saved.getSummary())
                .summarySource(saved == null ? "NONE" : saved.getSummarySource())
                .summaryStatus(saved == null ? "NOT_READY" : saved.getStatus())
                .model(saved == null ? null : saved.getModel()).generatedAt(saved == null ? null : saved.getGeneratedAt())
                .ruleVersion(basis == null ? null : basis.ruleVersion()).priorities(priorities)
                .items(result.getContent().stream().map(product -> mapper.toItem(product,
                        category != null ? category : order.stream().filter(product.getCategories()::contains).findFirst().orElse(order.get(0))))
                        .toList())
                .currentPage(page).totalPages(result.getTotalPages()).totalElements(result.getTotalElements()).hasNext(result.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public DrugResponseDTO.DrugListResultDTO withRecommendationSummary(
            Long userId, DrugResponseDTO.DrugListResultDTO catalog) {
        var report = findLatestCompletedReport(userId).orElse(null);
        var saved = report == null ? null : recommendations.findById(report.getId()).orElse(null);
        return catalog.toBuilder()
                .hasMeasurement(report != null)
                .reportId(report == null ? null : report.getId())
                .measurementSessionId(report == null ? null : report.getMeasurementSession().getId())
                .summary(saved == null ? null : saved.getSummary())
                .summaryStatus(saved == null ? "NOT_READY" : saved.getStatus())
                .summarySource(saved == null ? "NONE" : saved.getSummarySource())
                .build();
    }

    public void refresh(Long userId) {
        var latest = findLatestCompletedReport(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REPORT_NOT_FOUND));
        var prepared = store.prepare(userId, latest.getMeasurementSession().getId());
        generation.generate(prepared.reportId());
    }

    private Optional<Report> findLatestCompletedReport(Long userId) {
        requireUser(userId);
        return reports.findLatestCompleted(userId, MeasurementStatus.COMPLETED, PageRequest.of(0, 1))
                .stream().findFirst();
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new GeneralException(ErrorStatus._UNAUTHORIZED);
        }
    }
}
