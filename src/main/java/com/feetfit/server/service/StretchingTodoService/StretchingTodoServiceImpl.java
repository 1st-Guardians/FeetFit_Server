package com.feetfit.server.service.StretchingTodoService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.StretchingTodoHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.StretchingTodoConverter;
import com.feetfit.server.domain.UserStretchingTodoAssignment;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.repository.UserStretchingTodoAssignmentRepository;
import com.feetfit.server.web.dto.stretching.StretchingTodoRequestDTO;
import com.feetfit.server.web.dto.stretching.StretchingTodoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StretchingTodoServiceImpl implements StretchingTodoService {

    private final UserStretchingTodoAssignmentRepository userStretchingTodoAssignmentRepository;
    private final UserRepository userRepository;

    @Override
    public StretchingTodoResponseDTO.StretchingTodoListResponseDTO getMyStretchingTodos(Long userId) {
        validateUserExists(userId);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfNextDay = today.plusDays(1).atStartOfDay();

        List<UserStretchingTodoAssignment> assignments = userStretchingTodoAssignmentRepository.findTodayAssignmentsByUserId(
                userId,
                startOfDay,
                startOfNextDay
        );
        boolean hasTodayTodos = !assignments.isEmpty();
        return StretchingTodoConverter.toStretchingTodoListResponseDTO(assignments, hasTodayTodos);
    }

    @Override
    @Transactional
    public StretchingTodoResponseDTO.StretchingTodoInfoResponseDTO updateCompletion(
            Long userId,
            Long todoId,
            StretchingTodoRequestDTO.UpdateCompletionRequestDTO request
    ) {
        validateUserExists(userId);
        UserStretchingTodoAssignment assignment = userStretchingTodoAssignmentRepository.findByUserIdAndTodoId(userId, todoId)
                .orElseThrow(() -> new StretchingTodoHandler(ErrorStatus.STRETCHING_TODO_NOT_FOUND));

        assignment.updateCompletion(request.getIsCompleted());
        return StretchingTodoConverter.toStretchingTodoInfoResponseDTO(assignment);
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserHandler(ErrorStatus.USER_NOT_FOUND);
        }
    }
}
