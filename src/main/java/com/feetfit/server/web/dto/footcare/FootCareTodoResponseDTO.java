package com.feetfit.server.web.dto.footcare;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class FootCareTodoResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "스트레칭 투두 상세 응답")
    public static class FootCareTodoInfoResponseDTO {
        @Schema(description = "사용자 투두 ID. 완료 여부 변경 시 이 값을 path variable로 사용합니다.", example = "1")
        private Long todoId;

        @Schema(description = "스트레칭 제목", example = "수건으로 발 당기기")
        private String title;

        @Schema(description = "건강 타입", example = "HALLUX_VALGUS", allowableValues = {"ATHLETES_FOOT", "HALLUX_VALGUS", "SKIN_IRRITATION", "POSTURE", "FOOT_ENVIRONMENT"})
        private String healthType;

        @Schema(description = "스트레칭 영상 URL", example = "https://www.youtube.com/watch?v=stretching001")
        private String youtubeUrl;

        @Schema(description = "완료 여부", example = "false")
        private Boolean isCompleted;

        @Schema(description = "완료한 시각. 완료 전이면 null", example = "2026-05-20T10:30:00", nullable = true)
        private LocalDateTime completedAt;

        @Schema(description = "투두 날짜", example = "2026-05-20T00:00:00")
        private LocalDateTime todoDate;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "오늘 스트레칭 투두 목록 응답")
    public static class FootCareTodoListResponseDTO {
        @Schema(description = "오늘 조회된 투두 개수", example = "3")
        private Integer totalCount;

        @Schema(description = "오늘 투두 존재 여부", example = "true")
        private Boolean hasTodayTodos;

        @Schema(description = "조회 결과 메시지", example = "오늘의 스트레칭 투두를 조회했습니다.")
        private String message;

        @Schema(description = "오늘 스트레칭 투두 목록")
        private List<FootCareTodoInfoResponseDTO> todos;
    }
}
