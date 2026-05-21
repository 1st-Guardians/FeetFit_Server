package com.feetfit.server.service.ShoeService;

import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;

public interface ShoeCommandService {
    ShoeResponseDTO.ShoeClickResultDTO clickShoe(Long userId, Long shoeId);
}