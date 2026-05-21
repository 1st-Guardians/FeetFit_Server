package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MeasurementQueryServiceImplTest {

    @Mock
    private MeasurementSessionRepository measurementSessionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MeasurementQueryServiceImpl measurementQueryService;

    @Test
    void getTodayMeasurementStatus_existingCompletedMeasurement_returnsTrue() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        given(userRepository.existsById(1L)).willReturn(true);
        given(measurementSessionRepository.existsByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
                1L,
                MeasurementStatus.COMPLETED,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        )).willReturn(true);

        MeasurementResponseDTO.TodayMeasurementStatusResultDTO response =
                measurementQueryService.getTodayMeasurementStatus(1L);

        assertThat(response.getToday()).isEqualTo(today);
        assertThat(response.getHasTodayMeasurement()).isTrue();
    }

    @Test
    void getTodayMeasurementStatus_noCompletedMeasurement_returnsFalse() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        given(userRepository.existsById(1L)).willReturn(true);
        given(measurementSessionRepository.existsByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
                1L,
                MeasurementStatus.COMPLETED,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        )).willReturn(false);

        MeasurementResponseDTO.TodayMeasurementStatusResultDTO response =
                measurementQueryService.getTodayMeasurementStatus(1L);

        assertThat(response.getToday()).isEqualTo(today);
        assertThat(response.getHasTodayMeasurement()).isFalse();
    }

    @Test
    void getTodayMeasurementStatus_missingUser_throwsUserHandler() {
        given(userRepository.existsById(404L)).willReturn(false);

        assertThatThrownBy(() -> measurementQueryService.getTodayMeasurementStatus(404L))
                .isInstanceOf(UserHandler.class);

        then(measurementSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    void getWeeklyMeasurementStatus_existingCompletedMeasurements_returnsSundayToSaturdayStatuses() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate weekStartDate = today.minusDays(today.getDayOfWeek().getValue() % 7L);
        LocalDate weekEndExclusiveDate = weekStartDate.plusDays(7);
        LocalDate measuredDate = weekStartDate.plusDays(2);

        given(userRepository.existsById(1L)).willReturn(true);
        given(measurementSessionRepository.findByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
                1L,
                MeasurementStatus.COMPLETED,
                weekStartDate.atStartOfDay(),
                weekEndExclusiveDate.atStartOfDay()
        )).willReturn(List.of(measurementSession(measuredDate.atTime(9, 0))));

        MeasurementResponseDTO.WeeklyMeasurementStatusResultDTO response =
                measurementQueryService.getWeeklyMeasurementStatus(1L);

        assertThat(response.getToday()).isEqualTo(today);
        assertThat(response.getWeekStartDate()).isEqualTo(weekStartDate);
        assertThat(response.getWeekEndDate()).isEqualTo(weekStartDate.plusDays(6));
        assertThat(response.getHasWeeklyMeasurement()).isTrue();
        assertThat(response.getDailyStatuses()).hasSize(7);
        assertThat(response.getDailyStatuses().get(0).getDate()).isEqualTo(weekStartDate);
        assertThat(response.getDailyStatuses().get(0).getDayOfWeekKor()).isEqualTo("일");
        assertThat(response.getDailyStatuses().get(2).getDate()).isEqualTo(measuredDate);
        assertThat(response.getDailyStatuses().get(2).getHasMeasurement()).isTrue();
        assertThat(response.getDailyStatuses())
                .filteredOn(MeasurementResponseDTO.DailyMeasurementStatusDTO::getHasMeasurement)
                .hasSize(1);
    }

    @Test
    void getWeeklyMeasurementStatus_noCompletedMeasurements_returnsFalseStatuses() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate weekStartDate = today.minusDays(today.getDayOfWeek().getValue() % 7L);

        given(userRepository.existsById(1L)).willReturn(true);
        given(measurementSessionRepository.findByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
                1L,
                MeasurementStatus.COMPLETED,
                weekStartDate.atStartOfDay(),
                weekStartDate.plusDays(7).atStartOfDay()
        )).willReturn(List.of());

        MeasurementResponseDTO.WeeklyMeasurementStatusResultDTO response =
                measurementQueryService.getWeeklyMeasurementStatus(1L);

        assertThat(response.getHasWeeklyMeasurement()).isFalse();
        assertThat(response.getDailyStatuses()).hasSize(7);
        assertThat(response.getDailyStatuses())
                .allMatch(status -> !status.getHasMeasurement());
    }

    @Test
    void getWeeklyMeasurementStatus_missingUser_throwsUserHandler() {
        given(userRepository.existsById(404L)).willReturn(false);

        assertThatThrownBy(() -> measurementQueryService.getWeeklyMeasurementStatus(404L))
                .isInstanceOf(UserHandler.class);

        then(measurementSessionRepository).shouldHaveNoInteractions();
    }

    private static MeasurementSession measurementSession(LocalDateTime measuredAt) {
        return MeasurementSession.builder()
                .id(1L)
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(measuredAt)
                .build();
    }
}
