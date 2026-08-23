package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Selects one display metric from one RunRepeat snapshot.
 *
 * <p>The selector is deliberately fail-closed. It never substitutes a
 * FOREFOOT value for HEEL, never combines toe-box height with width, and never
 * chooses arbitrarily when two source rows have the same priority.</p>
 */
@Component
public class ShoeCharacteristicMetricSelector {

    public Optional<ShoeLabMetric> select(
            ShoeLabCharacteristic characteristic,
            Collection<ShoeLabMetric> snapshotMetrics) {
        List<ShoeLabMetric> eligible = snapshotMetrics.stream()
                .filter(metric -> metric.getCanonicalCharacteristic() == characteristic)
                .filter(metric -> metric.getValue() != null)
                .filter(metric -> isDisplayMetric(characteristic, metric))
                .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        int bestPriority = eligible.stream()
                .mapToInt(metric -> priority(characteristic, metric))
                .min()
                .orElse(Integer.MAX_VALUE);
        List<ShoeLabMetric> best = eligible.stream()
                .filter(metric -> priority(characteristic, metric) == bestPriority)
                .toList();

        return best.size() == 1 ? Optional.of(best.get(0)) : Optional.empty();
    }

    private boolean isDisplayMetric(
            ShoeLabCharacteristic characteristic,
            ShoeLabMetric metric) {
        return switch (characteristic) {
            case CUSHION -> hasSourceBase(metric, "Midsole softness")
                    && equalsNormalized(metric.getVariant(), "primary")
                    && isBlank(metric.getLocation())
                    && (equalsNormalized(metric.getUnit(), "AC")
                    || equalsNormalized(metric.getUnit(), "HA"));
            case SHOCK_ABSORPTION -> hasSourceBase(metric, "Shock absorption heel")
                    && equalsNormalized(metric.getLocation(), "HEEL")
                    && equalsNormalized(metric.getUnit(), "SA");
            case ENERGY_RETURN -> hasSourceBase(metric, "Energy return heel")
                    && equalsNormalized(metric.getLocation(), "HEEL")
                    && equalsNormalized(metric.getUnit(), "%");
            case WIDTH_SPACE -> hasSourceBase(metric, "Width / Fit")
                    && equalsNormalized(metric.getUnit(), "mm");
            case TOEBOX_SPACE -> hasSourceBase(metric, "Toebox width")
                    && equalsNormalized(metric.getVariant(), "width")
                    && equalsNormalized(metric.getUnit(), "mm");
            case HEEL_HOLD -> hasSourceBase(metric, "Heel counter stiffness")
                    && equalsNormalized(metric.getLocation(), "HEEL");
            case BREATHABILITY -> hasSourceBase(metric, "Breathability")
                    && (equalsNormalized(metric.getUnit(), "BR")
                    || equalsNormalized(metric.getUnit(), "score"));
        };
    }

    private boolean hasSourceBase(ShoeLabMetric metric, String expected) {
        String sourceName = normalize(metric.getSourceMetricName());
        if (sourceName == null) {
            return false;
        }
        String baseName = sourceName.replaceFirst(
                "\\s*\\((?:new|old) method\\)\\s*$", "");
        return baseName.equals(normalize(expected));
    }

    private int priority(ShoeLabCharacteristic characteristic, ShoeLabMetric metric) {
        return switch (characteristic) {
            // RunRepeat's current AC and BR methods are preferred. The legacy
            // method remains a fallback within the same snapshot, never a peer
            // in the same comparison distribution.
            case CUSHION -> equalsNormalized(metric.getUnit(), "AC") ? 0 : 1;
            case BREATHABILITY -> equalsNormalized(metric.getUnit(), "BR") ? 0 : 1;
            default -> 0;
        };
    }

    private boolean equalsNormalized(String left, String right) {
        return normalize(left) != null && normalize(left).equals(normalize(right));
    }

    private boolean isBlank(String value) {
        return normalize(value) == null;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
