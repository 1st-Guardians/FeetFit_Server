package com.feetfit.server.service.StretchingTodoService;

import com.feetfit.server.web.dto.stretching.StretchingTodoRequestDTO;
import com.feetfit.server.web.dto.stretching.StretchingTodoResponseDTO;

public interface StretchingTodoService {
    StretchingTodoResponseDTO.StretchingTodoListResponseDTO getMyStretchingTodos(Long userId);

    StretchingTodoResponseDTO.StretchingTodoInfoResponseDTO updateCompletion(
            Long userId,
            Long todoId,
            StretchingTodoRequestDTO.UpdateCompletionRequestDTO request
    );
}
