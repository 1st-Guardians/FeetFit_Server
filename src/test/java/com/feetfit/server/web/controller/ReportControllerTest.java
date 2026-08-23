package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.apiPayload.exception.handler.ReportHandler;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.ReportService.ReportCommandService;
import com.feetfit.server.service.ReportService.ReportQueryService;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ExceptionAdvice.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportCommandService reportCommandService;

    @MockBean
    private ReportQueryService reportQueryService;

    @MockBean
    private FindLoginUser findLoginUser;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void saveTinaPedisAnalysis_success_returnsAnalysis() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(reportCommandService.saveTinaPedisAnalysis(eq(1L), any(), any(), any(), any(), any()))
                .willReturn(tinaPedisResponse());

        MockMultipartFile request = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                """
                                {
                                  "measurementSessionId": 1,
                                  "fungalSuspicionSafetyScore": 82,
                                  "skinReactionSafetyScore": 76,
                                  "fungalSuspicionSafetyDescription": "발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.",
                                  "skinReactionSafetyDescription": "피부 발적과 자극 반응은 경미한 수준입니다.",
                                  "totalScoreDescription": "전반적으로 안전하지만 발 건조 관리가 필요합니다."
                                }
                                """.getBytes()
        );
        MockMultipartFile suspiciousAreaMapImage = new MockMultipartFile(
                "suspiciousAreaMapImage",
                "map.png",
                MediaType.IMAGE_PNG_VALUE,
                "map-image".getBytes()
        );
        MockMultipartFile originalFootImage = new MockMultipartFile(
                "originalFootImage",
                "foot.png",
                MediaType.IMAGE_PNG_VALUE,
                "foot-image".getBytes()
        );
        MockMultipartFile soleSuspiciousAreaMapImage = new MockMultipartFile(
                "soleSuspiciousAreaMapImage",
                "sole-map.png",
                MediaType.IMAGE_PNG_VALUE,
                "sole-map-image".getBytes()
        );
        MockMultipartFile soleOriginalFootImage = new MockMultipartFile(
                "soleOriginalFootImage",
                "sole-original.png",
                MediaType.IMAGE_PNG_VALUE,
                "sole-original-image".getBytes()
        );

        mockMvc.perform(multipart("/api/reports/tina-pedis")
                        .file(request)
                        .file(suspiciousAreaMapImage)
                        .file(originalFootImage)
                        .file(soleSuspiciousAreaMapImage)
                        .file(soleOriginalFootImage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.measurementSessionId").value(1L))
                .andExpect(jsonPath("$.result.fungalSuspicionSafetyScore").value(82))
                .andExpect(jsonPath("$.result.totalScore").value(80.2))
                .andExpect(jsonPath("$.result.previousTotalScore").value(74.5))
                .andExpect(jsonPath("$.result.totalScoreDiff").value(5.7));
    }

    @Test
    void getTinaPedisAnalysis_missingAnalysis_returnsNotFoundError() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(reportQueryService.getTinaPedisAnalysis(1L, LocalDate.of(2026, 5, 20)))
                .willThrow(new ReportHandler(ErrorStatus.TINA_PEDIS_ANALYSIS_NOT_FOUND));

        mockMvc.perform(get("/api/reports/tina-pedis")
                        .param("date", "2026-05-20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("TINA_PEDIS4001"))
                .andExpect(jsonPath("$.message").value("무좀 분석 결과를 찾을 수 없습니다."));
    }

    private static ReportResponseDTO.TinaPedisAnalysisResultDTO tinaPedisResponse() {
        return ReportResponseDTO.TinaPedisAnalysisResultDTO.builder()
                .id(1L)
                .measurementSessionId(1L)
                .fungalSuspicionSafetyScore(82)
                .skinReactionSafetyScore(76)
                .totalScore(80.2f)
                .previousTotalScore(74.5f)
                .totalScoreDiff(5.7f)
                .fungalSuspicionSafetyDescription("발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.")
                .skinReactionSafetyDescription("피부 발적과 자극 반응은 경미한 수준입니다.")
                .totalScoreDescription("전반적으로 안전하지만 발 건조 관리가 필요합니다.")
                .suspiciousAreaMapImageUrl("https://example.com/tina-pedis/map.png")
                .originalFootImageUrl("https://example.com/tina-pedis/original.png")
                .soleSuspiciousAreaMapImageUrl("https://example.com/tina-pedis/sole-map.png")
                .soleOriginalFootImageUrl("https://example.com/tina-pedis/sole-original.png")
                .recordedAt(LocalDateTime.of(2026, 5, 20, 9, 0))
                .createdAt(LocalDateTime.of(2026, 5, 20, 9, 0))
                .updatedAt(LocalDateTime.of(2026, 5, 20, 9, 0))
                .build();
    }
}
