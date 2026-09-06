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
            Boolean hasTodos
    ) {
        List<FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO> todoResponses = assignments.stream()
                .map(FootCareTodoConverter::toFootCareTodoInfoResponseDTO)
                .toList();

        return FootCareTodoResponseDTO.FootCareTodoListResponseDTO.builder()
                .totalCount(todoResponses.size())
                .hasTodayTodos(hasTodos)
                .message(todoResponses.isEmpty()
                        ? "연결된 발 관리 투두가 없습니다."
                        : "최근 발 분석 기반 발 관리 투두입니다.")
                .todos(todoResponses)
                .build();
    }
}
