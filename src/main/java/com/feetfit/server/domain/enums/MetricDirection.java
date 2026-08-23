package com.feetfit.server.domain.enums;

/**
 * Describes how a raw metric value maps to the user-facing characteristic level.
 */
public enum MetricDirection {
    /** A larger raw value represents a higher characteristic level. */
    HIGHER_IS_HIGH,

    /** A smaller raw value represents a higher characteristic level. */
    LOWER_IS_HIGH;

    public ShoeCharacteristicLevel applyTo(ShoeCharacteristicLevel rawLevel) {
        if (this == HIGHER_IS_HIGH || rawLevel == ShoeCharacteristicLevel.MEDIUM) {
            return rawLevel;
        }

        return rawLevel == ShoeCharacteristicLevel.LOW
                ? ShoeCharacteristicLevel.HIGH
                : ShoeCharacteristicLevel.LOW;
    }
}
