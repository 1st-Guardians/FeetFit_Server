package com.feetfit.server.service.DrugService;

import com.feetfit.server.web.dto.drug.DrugResponseDTO;

public interface DrugQueryService {

    DrugResponseDTO.DrugCategoryResultDTO getCategories();

    DrugResponseDTO.DrugListResultDTO getDrugList(String category, String query, int page, int size);

    DrugResponseDTO.DrugPermitResultDTO getPermitInfo(String itemSeq);
}
