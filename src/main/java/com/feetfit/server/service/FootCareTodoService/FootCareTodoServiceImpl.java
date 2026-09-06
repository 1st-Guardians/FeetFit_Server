package com.feetfit.server.service.FootCareTodoService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.FootCareTodoHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.FootCareTodoConverter;
import com.feetfit.server.domain.UserFootCareTodoAssignment;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.repository.UserFootCareTodoAssignmentRepository;
import com.feetfit.server.web.dto.footcare.FootCareTodoRequestDTO;
import com.feetfit.server.web.dto.footcare.FootCareTodoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FootCareTodoServiceImpl implements FootCareTodoService {

    private final UserFootCareTodoAssignmentRepository userFootCareTodoAssignmentRepository;
    private final UserRepository userRepository;

    @Override
    public FootCareTodoResponseDTO.FootCareTodoListResponseDTO getMyFootCareTodos(Long userId) {
        validateUserExists(userId);
        List<UserFootCareTodoAssignment> assignments =
                userFootCareTodoAssignmentRepository.findLatestAssignmentsByUserId(userId);
        return FootCareTodoConverter.toFootCareTodoListResponseDTO(assignments, !assignments.isEmpty());
    }

    @Override
    @Transactional
    public FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO updateCompletion(
            Long userId,
            Long todoId,
            FootCareTodoRequestDTO.UpdateCompletionRequestDTO request
    ) {
        validateUserExists(userId);
        UserFootCareTodoAssignment assignment = userFootCareTodoAssignmentRepository.findByUserIdAndTodoId(userId, todoId)
                .orElseThrow(() -> new FootCareTodoHandler(ErrorStatus.FOOT_CARE_TODO_NOT_FOUND));

        assignment.updateCompletion(request.getIsCompleted());
        return FootCareTodoConverter.toFootCareTodoInfoResponseDTO(assignment);
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserHandler(ErrorStatus.USER_NOT_FOUND);
        }
    }
}
