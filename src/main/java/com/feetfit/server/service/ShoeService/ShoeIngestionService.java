package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.enums.ShoeImportMatchStatus;
import com.feetfit.server.domain.enums.ShoeImportSource;
import com.feetfit.server.web.dto.shoe.ShoeIngestionRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeIngestionResponseDTO;

public interface ShoeIngestionService {
    ShoeIngestionResponseDTO.ImportResult importMusinsa(
            ShoeIngestionRequestDTO.MusinsaImportRequest request);

    ShoeIngestionResponseDTO.ImportResult importRunRepeat(
            ShoeIngestionRequestDTO.RunRepeatImportRequest request);

    ShoeIngestionResponseDTO.ImportResult importRunRepeatTargeted(
            ShoeIngestionRequestDTO.RunRepeatImportRequest request);

    ShoeIngestionResponseDTO.AuditPageResult getImportAudits(
            ShoeImportSource source,
            ShoeImportMatchStatus matchStatus,
            int page,
            int size);
}
