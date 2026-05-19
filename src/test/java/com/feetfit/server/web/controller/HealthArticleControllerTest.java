package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.HealthArticleService.HealthArticleService;
import com.feetfit.server.web.dto.health.HealthArticleResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthArticleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ExceptionAdvice.class)
class HealthArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthArticleService healthArticleService;

    @MockBean
    private FindLoginUser findLoginUser;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void getMyHealthArticles_success_returnsArticles() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(healthArticleService.getMyHealthArticles(1L)).willReturn(articleListResponse());

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.totalCount").value(1))
                .andExpect(jsonPath("$.result.articles[0].articleId").value(1L))
                .andExpect(jsonPath("$.result.articles[0].title").value("무지외반증 예방을 위한 발 관리"))
                .andExpect(jsonPath("$.result.articles[0].healthType").value("HALLUX_VALGUS"));
    }

    @Test
    void getMyHealthArticles_missingUser_returnsNotFoundError() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(404L);
        given(healthArticleService.getMyHealthArticles(404L))
                .willThrow(new UserHandler(ErrorStatus.USER_NOT_FOUND));

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("USER4001"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
    }

    private static HealthArticleResponseDTO.HealthArticleListResponseDTO articleListResponse() {
        return HealthArticleResponseDTO.HealthArticleListResponseDTO.builder()
                .totalCount(1)
                .articles(List.of(articleInfoResponse()))
                .build();
    }

    private static HealthArticleResponseDTO.HealthArticleInfoResponseDTO articleInfoResponse() {
        return HealthArticleResponseDTO.HealthArticleInfoResponseDTO.builder()
                .articleId(1L)
                .title("무지외반증 예방을 위한 발 관리")
                .url("https://example.com/articles/hallux-valgus-care")
                .publisher("FeetFit")
                .publishedAt(LocalDateTime.of(2026, 5, 20, 9, 0))
                .healthType("HALLUX_VALGUS")
                .description("무지외반증 예방을 위한 생활 습관과 스트레칭 정보를 제공합니다.")
                .build();
    }
}
