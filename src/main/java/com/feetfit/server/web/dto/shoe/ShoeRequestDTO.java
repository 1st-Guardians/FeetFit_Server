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
        private ShoeSort sort;
        private int page;
        private int size;
    }
}