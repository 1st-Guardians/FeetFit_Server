package com.feetfit.server.converter;

import com.feetfit.server.domain.UserStretchingTodo;
import com.feetfit.server.domain.UserStretchingTodoAssignment;
import com.feetfit.server.web.dto.stretching.StretchingTodoResponseDTO;

import java.util.List;

public class StretchingTodoConverter {

    public static StretchingTodoResponseDTO.StretchingTodoInfoResponseDTO toStretchingTodoInfoResponseDTO(
            UserStretchingTodoAssignment assignment
    ) {
        UserStretchingTodo todo = assignment.getStretchingTodo();

        return StretchingTodoResponseDTO.StretchingTodoInfoResponseDTO.builder()
                .todoId(todo.getId())
                .title(todo.getTitle())
                .healthType(todo.getHealthType().name())
                .youtubeUrl(todo.getYoutubeUrl())
                .isCompleted(assignment.getIsCompleted())
                .completedAt(assignment.getCompletedAt())
                .todoDate(todo.getTodoDate())
                .build();
    }

    public static StretchingTodoResponseDTO.StretchingTodoListResponseDTO toStretchingTodoListResponseDTO(
            List<UserStretchingTodoAssignment> assignments,
            Boolean hasTodayTodos
    ) {
        List<StretchingTodoResponseDTO.StretchingTodoInfoResponseDTO> todoResponses = assignments.stream()
                .map(StretchingTodoConverter::toStretchingTodoInfoResponseDTO)
                .toList();

        return StretchingTodoResponseDTO.StretchingTodoListResponseDTO.builder()
                .totalCount(todoResponses.size())
                .hasTodayTodos(hasTodayTodos)
                .message(todoResponses.isEmpty()
                        ? "오늘 스트레칭 투두가 없습니다."
                        : "오늘 스트레칭 투두입니다.")
                .todos(todoResponses)
                .build();
    }
}
