package com.feetfit.server.service.DrugService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.drug.DrugProduct;
import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DrugCatalogMapper {
    static final String EASY_DRUG_SOURCE = "식품의약품안전처 e약은요";
    static final String ONLINE_SOURCE = "온라인 구매 가능 제품 안내";
    static final String PERMIT_SOURCE = "식품의약품안전처 의약품 제품 허가정보";

    private final ObjectMapper objectMapper;

    public String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize drug catalog data", e);
        }
    }

    public <T> T deserialize(String payload, Class<T> type) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read stored drug catalog data", e);
        }
    }

    public DrugResponseDTO.DrugItemDTO toItem(DrugProduct product, DrugCategory category) {
        DrugResponseDTO.DrugItemDTO item = deserialize(product.getItemPayload(), DrugResponseDTO.DrugItemDTO.class);
        return item.toBuilder()
                .category(!product.isOnline() && category != null ? category.getId() : item.getCategory())
                .permit(deserialize(product.getPermitPayload(), DrugResponseDTO.PermitInfoDTO.class))
                .build();
    }

    public DrugResponseDTO.RecallInfoDTO toRecall(DrugProduct product) {
        if (product.getRecallPayload() != null) {
            return deserialize(product.getRecallPayload(), DrugResponseDTO.RecallInfoDTO.class);
        }
        return DrugResponseDTO.RecallInfoDTO.builder()
                .status("unavailable").records(List.of())
                .sourceUrl("https://www.data.go.kr/data/15059114/openapi.do")
                .build();
    }
}
