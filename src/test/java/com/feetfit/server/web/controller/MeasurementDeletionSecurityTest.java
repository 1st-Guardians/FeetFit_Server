package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.config.SecurityConfig;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.jwt.JwtAccessDeniedHandler;
import com.feetfit.server.jwt.JwtAuthenticationEntryPoint;
import com.feetfit.server.jwt.JwtFilter;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.MeasurementService.MeasurementCommandService;
import com.feetfit.server.service.MeasurementService.MeasurementQueryService;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeasurementController.class)
@Import({SecurityConfig.class, JwtFilter.class, JwtAccessDeniedHandler.class,
        JwtAuthenticationEntryPoint.class, FindLoginUser.class, ExceptionAdvice.class})
class MeasurementDeletionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeasurementCommandService measurementCommandService;

    @MockBean
    private MeasurementQueryService measurementQueryService;

    @MockBean
    private MeasurementSocketService measurementSocketService;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void deleteMeasurementRecords_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/measurement-sessions/59/records"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));

        verifyNoInteractions(measurementCommandService);
    }

    @Test
    void deleteMeasurementRecords_invalidToken_returnsUnauthorized() throws Exception {
        given(tokenProvider.validateToken("invalid-token")).willReturn(false);

        mockMvc.perform(delete("/api/measurement-sessions/59/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));

        verifyNoInteractions(measurementCommandService);
    }

    @Test
    @WithMockUser(username = "7")
    void deleteMeasurementRecords_usesAuthenticatedUserInsteadOfSuppliedUserId() throws Exception {
        given(measurementCommandService.deleteMeasurementRecords(7L, 59L))
                .willReturn(MeasurementResponseDTO.DeleteMeasurementRecordsResultDTO.builder()
                        .measurementSessionId(59L)
                        .deletedShoeRecommendationCount(1)
                        .deletedMeasurementSessionCount(1)
                        .build());

        mockMvc.perform(delete("/api/measurement-sessions/59/records").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.measurementSessionId").value(59))
                .andExpect(jsonPath("$.result.deletedShoeRecommendationCount").value(1));

        verify(measurementCommandService).deleteMeasurementRecords(7L, 59L);
    }

    @Test
    @WithMockUser(username = "7")
    void deleteMeasurementRecords_otherUserSession_returnsForbidden() throws Exception {
        given(measurementCommandService.deleteMeasurementRecords(7L, 59L))
                .willThrow(new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN));

        mockMvc.perform(delete("/api/measurement-sessions/59/records"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEASUREMENT4003"));
    }
}
