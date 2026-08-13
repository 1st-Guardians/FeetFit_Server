package com.feetfit.server.converter;

import com.feetfit.server.domain.UserFootCareTodo;
import com.feetfit.server.domain.UserFootCareTodoAssignment;
import com.feetfit.server.web.dto.footcare.FootCareTodoResponseDTO;

import java.util.List;

public class FootCareTodoConverter {

    public static FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO toFootCareTodoInfoResponseDTO(
            UserFootCareTodoAssignment assignment
    ) {
        UserFootCareTodo todo = assignment.getFootCareTodo();

        return FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO.builder()
                .todoId(todo.getId())
                .title(todo.getTitle())
                .healthType(todo.getHealthType().name())
                .youtubeUrl(todo.getYoutubeUrl())
                .isCompleted(assignment.getIsCompleted())
                .completedAt(assignment.getCompletedAt())
                .todoDate(assignment.getCreatedAt())
                .build();
    }

    public static FootCareTodoResponseDTO.FootCareTodoListResponseDTO toFootCareTodoListResponseDTO(
            List<UserFootCareTodoAssignment> assignments,
            Boolean hasTodayTodos
    ) {
        List<FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO> todoResponses = assignments.stream()
                .map(FootCareTodoConverter::toFootCareTodoInfoResponseDTO)
                .toList();

        return FootCareTodoResponseDTO.FootCareTodoListResponseDTO.builder()
                .totalCount(todoResponses.size())
                .hasTodayTodos(hasTodayTodos)
                .message(todoResponses.isEmpty()
                        ? "오늘 발 관리 투두가 없습니다."
                        : "오늘 발 관리 투두입니다.")
                .todos(todoResponses)
                .build();
    }
}
