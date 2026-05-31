package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.MeasurementService.MeasurementCommandService;
import com.feetfit.server.service.MeasurementService.MeasurementQueryService;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeasurementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ExceptionAdvice.class)
class MeasurementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeasurementCommandService measurementCommandService;

    @MockBean
    private MeasurementQueryService measurementQueryService;

    @MockBean
    private MeasurementSocketService measurementSocketService;

    @MockBean
    private FindLoginUser findLoginUser;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void getTodayMeasurementStatus_success_returnsTodayStatus() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(measurementQueryService.getTodayMeasurementStatus(1L))
                .willReturn(todayMeasurementStatusResponse(true));

        mockMvc.perform(get("/api/measurement-sessions/today-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.today").value("2026-05-20"))
                .andExpect(jsonPath("$.result.hasTodayMeasurement").value(true));
    }

    @Test
    void updateMeasurementStatus_toCompleted_success_returnsCompletedStatus() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(measurementCommandService.updateMeasurementStatus(
                eq(1L),
                eq(9L),
                org.mockito.ArgumentMatchers.argThat(request ->
                        request.getStatus() == MeasurementStatus.COMPLETED
                                && request.getMeasurementDurationSec().equals(180)
                )
        ))
                .willReturn(updateMeasurementStatusResponse());

        mockMvc.perform(patch("/api/measurement-sessions/{measurementSessionId}/status", 9L)
                        .param("status", "COMPLETED")
                        .param("measurementDurationSec", "180"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.id").value(9))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.measurementDurationSec").value(180));
    }

    @Test
    void getTodayMeasurementStatus_missingUser_returnsNotFoundError() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(404L);
        given(measurementQueryService.getTodayMeasurementStatus(404L))
                .willThrow(new UserHandler(ErrorStatus.USER_NOT_FOUND));

        mockMvc.perform(get("/api/measurement-sessions/today-status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("USER4001"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
    }

    @Test
    void getWeeklyMeasurementStatus_success_returnsWeeklyStatuses() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(measurementQueryService.getWeeklyMeasurementStatus(1L))
                .willReturn(weeklyMeasurementStatusResponse());

        mockMvc.perform(get("/api/measurement-sessions/weekly-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.today").value("2026-05-20"))
                .andExpect(jsonPath("$.result.weekStartDate").value("2026-05-17"))
                .andExpect(jsonPath("$.result.weekEndDate").value("2026-05-23"))
                .andExpect(jsonPath("$.result.hasWeeklyMeasurement").value(true))
                .andExpect(jsonPath("$.result.dailyStatuses").isArray())
                .andExpect(jsonPath("$.result.dailyStatuses[0].dayOfWeekKor").value("일"))
                .andExpect(jsonPath("$.result.dailyStatuses[3].date").value("2026-05-20"))
                .andExpect(jsonPath("$.result.dailyStatuses[3].hasMeasurement").value(true));
    }

    @Test
    void getWeeklyMeasurementStatus_missingUser_returnsNotFoundError() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(404L);
        given(measurementQueryService.getWeeklyMeasurementStatus(404L))
                .willThrow(new UserHandler(ErrorStatus.USER_NOT_FOUND));

        mockMvc.perform(get("/api/measurement-sessions/weekly-status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("USER4001"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
    }

    private static MeasurementResponseDTO.WeeklyMeasurementStatusResultDTO weeklyMeasurementStatusResponse() {
        return MeasurementResponseDTO.WeeklyMeasurementStatusResultDTO.builder()
                .today(LocalDate.of(2026, 5, 20))
                .weekStartDate(LocalDate.of(2026, 5, 17))
                .weekEndDate(LocalDate.of(2026, 5, 23))
                .hasWeeklyMeasurement(true)
                .dailyStatuses(List.of(
                        dailyStatus(LocalDate.of(2026, 5, 17), "SUNDAY", "일", false),
                        dailyStatus(LocalDate.of(2026, 5, 18), "MONDAY", "월", false),
                        dailyStatus(LocalDate.of(2026, 5, 19), "TUESDAY", "화", false),
                        dailyStatus(LocalDate.of(2026, 5, 20), "WEDNESDAY", "수", true),
                        dailyStatus(LocalDate.of(2026, 5, 21), "THURSDAY", "목", false),
                        dailyStatus(LocalDate.of(2026, 5, 22), "FRIDAY", "금", false),
                        dailyStatus(LocalDate.of(2026, 5, 23), "SATURDAY", "토", false)
                ))
                .build();
    }

    private static MeasurementResponseDTO.TodayMeasurementStatusResultDTO todayMeasurementStatusResponse(
            Boolean hasTodayMeasurement
    ) {
        return MeasurementResponseDTO.TodayMeasurementStatusResultDTO.builder()
                .today(LocalDate.of(2026, 5, 20))
                .hasTodayMeasurement(hasTodayMeasurement)
                .build();
    }

    private static MeasurementResponseDTO.UpdateMeasurementStatusResultDTO updateMeasurementStatusResponse() {
        return MeasurementResponseDTO.UpdateMeasurementStatusResultDTO.builder()
                .id(9L)
                .status(MeasurementStatus.COMPLETED)
                .measurementDurationSec(180)
                .updatedAt(LocalDateTime.of(2026, 5, 20, 9, 3))
                .build();
    }

    private static MeasurementResponseDTO.DailyMeasurementStatusDTO dailyStatus(
            LocalDate date,
            String dayOfWeek,
            String dayOfWeekKor,
            Boolean hasMeasurement
    ) {
        return MeasurementResponseDTO.DailyMeasurementStatusDTO.builder()
                .date(date)
                .dayOfWeek(dayOfWeek)
                .dayOfWeekKor(dayOfWeekKor)
                .hasMeasurement(hasMeasurement)
                .build();
    }
}
