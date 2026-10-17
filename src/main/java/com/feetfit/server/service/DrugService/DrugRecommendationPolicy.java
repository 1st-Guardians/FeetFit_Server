package com.feetfit.server.service.DrugService;

import com.feetfit.server.converter.ReportConverter;
import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.domain.enums.MetricType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class DrugRecommendationPolicy {
    public static final String RULE_VERSION = "drug-priority-v2-healthy-skin-first-hva-15-20-40";
    public static final List<DrugCategory> DEFAULT_ORDER = List.of(
            DrugCategory.WOUND_CARE, DrugCategory.ATHLETES_FOOT, DrugCategory.BUNION_BAND);
    public static final String HEALTHY_SUMMARY = "측정 지표가 전반적으로 양호하게 나타났어요. 아래 제품은 필요할 때 참고하실 수 있도록 추천드리며, 현재 측정 결과가 별도의 약 사용이 필요하다는 의미는 아니에요.";

    public record Priority(DrugCategory category, MetricType metricType, Double safetyScore,
                           Double priorityScore, Double leftToeAngleDegree, Double rightToeAngleDegree,
                           Integer hvaLevel, String reason) {
    }

    public record Basis(String ruleVersion, List<Priority> priorities, boolean allHealthy) {
    }

    public Basis rank(Float skinSafety, Float fungalSafety, Float leftAngle, Float rightAngle) {
        List<Priority> priorities = new ArrayList<>();
        priorities.add(safety(DrugCategory.ATHLETES_FOOT, MetricType.ATHLETES_FOOT, fungalSafety, "무좀 관련"));
        priorities.add(safety(DrugCategory.WOUND_CARE, MetricType.SKIN_IRRITATION, skinSafety, "피부 반응"));
        Double left = angle(leftAngle);
        Double right = angle(rightAngle);
        Double worst = left;
        if (worst == null || (right != null && right > worst)) {
            worst = right;
        }
        Integer level = worst == null ? null : worst <= 15 ? 1 : worst <= 20 ? 2 : worst <= 40 ? 3 : 4;
        // These points compare product categories inside the app; they are not a clinical risk scale.
        Double priority = level == null ? null : switch (level) {
            case 1 -> 0.0;
            case 2 -> 33.0;
            case 3 -> 67.0;
            default -> 100.0;
        };
        priorities.add(new Priority(DrugCategory.BUNION_BAND, MetricType.HALLUX_VALGUS, null, priority,
                left, right, level, worst == null ? "무지외반 각도 측정값이 없습니다."
                : ReportConverter.generateHvaAnalysisText(worst.floatValue())));
        boolean allHealthy = priorities.stream().allMatch(item ->
                item.priorityScore() != null && item.priorityScore() == 0.0);
        if (allHealthy) {
            priorities.sort(Comparator.comparingInt(item -> DEFAULT_ORDER.indexOf(item.category())));
        } else {
            priorities.sort(Comparator.comparing(Priority::priorityScore,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return new Basis(RULE_VERSION, List.copyOf(priorities), allHealthy);
    }

    public String fallbackSummary(Basis basis) {
        if (basis.allHealthy()) {
            return HEALTHY_SUMMARY;
        }
        Priority first = basis.priorities().stream().filter(item -> item.priorityScore() != null && item.priorityScore() > 0)
                .findFirst().orElse(null);
        if (first == null) {
            return "측정 정보가 충분하지 않아 취약한 부위를 판단하기 어렵습니다. 제품 정보를 확인하고 필요하면 전문가와 상담해 주세요.";
        }
        return switch (first.category()) {
            case ATHLETES_FOOT -> String.format(Locale.ROOT,
                    "무좀 관련 안전점수가 %.0f점으로 세 지표 중 우선 관리가 필요한 것으로 나타나, 약사 상담에 참고할 무좀약 정보를 먼저 안내해 드려요.", first.safetyScore());
            case WOUND_CARE -> String.format(Locale.ROOT,
                    "피부 반응 안전점수가 %.0f점으로 세 지표 중 우선 관리가 필요한 것으로 나타나, 상처 보호·발 관리 제품 정보를 먼저 안내해 드려요.", first.safetyScore());
            case BUNION_BAND -> first.reason() + " 발가락 보호·교정밴드 등 보조용품 정보를 먼저 안내하며, 사용 전 전문가와 상담해 주세요.";
        };
    }

    private Priority safety(DrugCategory category, MetricType metric, Float score, String label) {
        Double valid = score != null && Float.isFinite(score) && score >= 0 && score <= 100 ? score.doubleValue() : null;
        return new Priority(category, metric, valid, valid == null ? null : 100.0 - valid,
                null, null, null, valid == null ? label + " 안전점수가 없습니다."
                : String.format(Locale.ROOT, "%s 안전점수 %.0f점이며, 안전점수가 낮을수록 우선 안내합니다.", label, valid));
    }

    private Double angle(Float value) {
        return value != null && Float.isFinite(value) && value >= 0 ? value.doubleValue() : null;
    }
}
