package com.feetfit.server.service.DrugService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.domain.drug.DrugProduct;
import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.repository.drug.DrugProductRepository;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;

import static com.feetfit.server.service.DrugService.DrugCatalogMapper.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DrugQueryServiceImpl implements DrugQueryService {
    private final DrugProductRepository products;
    private final DrugCatalogMapper mapper;

    @Override
    public DrugResponseDTO.DrugCategoryResultDTO getCategories() {
        return DrugResponseDTO.DrugCategoryResultDTO.builder()
                .categories(Arrays.stream(DrugCategory.values()).map(category ->
                        DrugResponseDTO.DrugCategoryDTO.builder()
                                .id(category.getId()).label(category.getLabel())
                                .eyebrow(category.getEyebrow()).description(category.getDescription())
                                .online(category.isOnline()).accent(category.getAccent())
                                .iconName(category.getIconName()).build()).toList())
                .build();
    }

    @Override
    public DrugResponseDTO.DrugListResultDTO getDrugList(String categoryId, String query, int page, int size) {
        DrugCategory category = DrugCategory.fromIdOrDefault(categoryId);
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (keyword.length() > 50) {
            keyword = keyword.substring(0, 50);
        }
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Page<DrugProduct> result = products.search(category, keyword, pattern, PageRequest.of(page, size));
        if (result.isEmpty() && !products.existsInCategory(category)) {
            throw new GeneralException(ErrorStatus.DRUG_DATA_SERVICE_UNAVAILABLE,
                    "의약품 데이터를 수집 중이에요. 잠시 후 다시 시도해 주세요.");
        }
        String source = switch (category) {
            case ATHLETES_FOOT -> EASY_DRUG_SOURCE;
            case BUNION_BAND -> ONLINE_SOURCE;
            case WOUND_CARE -> EASY_DRUG_SOURCE + " + " + ONLINE_SOURCE;
        };
        return DrugResponseDTO.DrugListResultDTO.builder()
                .category(category.getId()).categoryLabel(category.getLabel()).source(source)
                .items(result.map(product -> mapper.toItem(product, category)).getContent())
                .currentPage(page).totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements()).hasNext(result.hasNext())
                .build();
    }

    @Override
    public DrugResponseDTO.DrugPermitResultDTO getPermitInfo(String itemSeq) {
        if (itemSeq == null || !itemSeq.matches("^\\d{6,20}$")) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "올바른 품목기준코드가 필요해요.");
        }
        DrugProduct product = products.findById(itemSeq)
                .orElseThrow(() -> new GeneralException(ErrorStatus.DRUG_PERMIT_NOT_FOUND));
        DrugResponseDTO.DrugItemDTO drug = mapper.toItem(product, null);
        if (drug.getPermit() == null) {
            throw new GeneralException(ErrorStatus.DRUG_PERMIT_NOT_FOUND);
        }
        return DrugResponseDTO.DrugPermitResultDTO.builder()
                .drug(drug).permit(drug.getPermit()).recall(mapper.toRecall(product))
                .source(EASY_DRUG_SOURCE + " + " + PERMIT_SOURCE)
                .build();
    }
}
