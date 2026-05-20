package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.apiPayload.exception.handler.StretchingTodoHandler;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.StretchingTodoService.StretchingTodoService;
import com.feetfit.server.web.dto.stretching.StretchingTodoResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StretchingTodoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ExceptionAdvice.class)
class StretchingTodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StretchingTodoService stretchingTodoService;

    @MockBean
    private FindLoginUser findLoginUser;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void getMyStretchingTodos_success_returnsTodos() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(stretchingTodoService.getMyStretchingTodos(1L)).willReturn(todoListResponse());

        mockMvc.perform(get("/api/stretching-todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.totalCount").value(1))
                .andExpect(jsonPath("$.result.hasTodayTodos").value(true))
                .andExpect(jsonPath("$.result.message").value("오늘 스트레칭 투두입니다."))
                .andExpect(jsonPath("$.result.todos[0].todoId").value(1L))
                .andExpect(jsonPath("$.result.todos[0].title").value("수건으로 발 당기기"))
                .andExpect(jsonPath("$.result.todos[0].healthType").value("POSTURE"))
                .andExpect(jsonPath("$.result.todos[0].youtubeUrl").value("https://www.youtube.com/watch?v=stretching"))
                .andExpect(jsonPath("$.result.todos[0].isCompleted").value(false))
                .andExpect(jsonPath("$.result.todos[0].completedAt").doesNotExist());
    }

    @Test
    void updateCompletion_success_returnsUpdatedTodo() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(stretchingTodoService.updateCompletion(eq(1L), eq(1L), any()))
                .willReturn(todoInfoResponse(true));

        mockMvc.perform(patch("/api/stretching-todos/1/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isCompleted": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.todoId").value(1L))
                .andExpect(jsonPath("$.result.title").value("수건으로 발 당기기"))
                .andExpect(jsonPath("$.result.healthType").value("POSTURE"))
                .andExpect(jsonPath("$.result.isCompleted").value(true))
                .andExpect(jsonPath("$.result.completedAt").value("2026-05-20T09:10:00"));
    }

    @Test
    void updateCompletion_missingIsCompleted_returnsValidationError() throws Exception {
        mockMvc.perform(patch("/api/stretching-todos/1/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.isCompleted").value("완료 여부는 필수입니다."));
    }

    @Test
    void updateCompletion_missingTodo_returnsNotFoundError() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(stretchingTodoService.updateCompletion(eq(1L), eq(404L), any()))
                .willThrow(new StretchingTodoHandler(ErrorStatus.STRETCHING_TODO_NOT_FOUND));

        mockMvc.perform(patch("/api/stretching-todos/404/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isCompleted": true
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("STRETCHING_TODO4001"))
                .andExpect(jsonPath("$.message").value("스트레칭 투두를 찾을 수 없습니다."));
    }

    private static StretchingTodoResponseDTO.StretchingTodoListResponseDTO todoListResponse() {
        return StretchingTodoResponseDTO.StretchingTodoListResponseDTO.builder()
                .totalCount(1)
                .hasTodayTodos(true)
                .message("오늘 스트레칭 투두입니다.")
                .todos(List.of(todoInfoResponse(false)))
                .build();
    }

    private static StretchingTodoResponseDTO.StretchingTodoInfoResponseDTO todoInfoResponse(Boolean isCompleted) {
        return StretchingTodoResponseDTO.StretchingTodoInfoResponseDTO.builder()
                .todoId(1L)
                .title("수건으로 발 당기기")
                .healthType("POSTURE")
                .youtubeUrl("https://www.youtube.com/watch?v=stretching")
                .isCompleted(isCompleted)
                .completedAt(Boolean.TRUE.equals(isCompleted) ? LocalDateTime.of(2026, 5, 20, 9, 10) : null)
                .todoDate(LocalDateTime.of(2026, 5, 20, 9, 0))
                .build();
    }
}
