package com.feetfit.server.web.dto.stretching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class StretchingTodoResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StretchingTodoInfoResponseDTO {
        private Long todoId;
        private String title;
        private String healthType;
        private String youtubeUrl;
        private Boolean isCompleted;
        private LocalDateTime completedAt;
        private LocalDateTime todoDate;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StretchingTodoListResponseDTO {
        private Integer totalCount;
        private Boolean hasTodayTodos;
        private String message;
        private List<StretchingTodoInfoResponseDTO> todos;
    }
}
