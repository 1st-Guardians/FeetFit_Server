package com.feetfit.server.service.ShoeService;

import com.feetfit.server.web.dto.shoe.ShoeCharacteristicResponseDTO;

/** Read-only access to objective RunRepeat shoe characteristics. */
public interface ShoeCharacteristicQueryService {

    ShoeCharacteristicResponseDTO.Result getCharacteristics(Long shoeId);
}
