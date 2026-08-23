package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.enums.ShoeCharacteristicLevel;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ShoeCharacteristicResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private Long shoeId;
        private String summary;

        @Builder.Default
        private List<Item> characteristics = new ArrayList<>();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private ShoeLabCharacteristic type;
        private ShoeCharacteristicLevel level;
        private BigDecimal value;
        private BigDecimal averageValue;
        private BigDecimal minValue;
        private BigDecimal maxValue;
        private String unit;
        private String testedSize;
    }
}
