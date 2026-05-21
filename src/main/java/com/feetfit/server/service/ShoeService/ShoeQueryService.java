package com.feetfit.server.service.ShoeService;

import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;

public interface ShoeQueryService {
    ShoeResponseDTO.ShoeListResultDTO getShoeList(Long userId, ShoeRequestDTO.ShoeListRequestDTO request);
}