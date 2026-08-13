package com.feetfit.server.service.FootCareTodoService;

import com.feetfit.server.web.dto.footcare.FootCareTodoRequestDTO;
import com.feetfit.server.web.dto.footcare.FootCareTodoResponseDTO;

public interface FootCareTodoService {
    FootCareTodoResponseDTO.FootCareTodoListResponseDTO getMyFootCareTodos(Long userId);

    FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO updateCompletion(
            Long userId,
            Long todoId,
            FootCareTodoRequestDTO.UpdateCompletionRequestDTO request
    );
}
