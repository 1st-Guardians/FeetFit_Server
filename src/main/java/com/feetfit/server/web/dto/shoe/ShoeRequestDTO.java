package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.enums.ShoeSort;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ShoeRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeListRequestDTO {
        private ShoeSort sort = ShoeSort.FIT_SCORE;
        private int page = 0;
        private int size = 20;
    }
}