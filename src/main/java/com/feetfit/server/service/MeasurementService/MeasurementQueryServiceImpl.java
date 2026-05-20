package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MeasurementQueryServiceImpl implements MeasurementQueryService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final MeasurementSessionRepository measurementSessionRepository;
    private final UserRepository userRepository;

    @Override
    public MeasurementResponseDTO.TodayMeasurementStatusResultDTO getTodayMeasurementStatus(Long userId) {
        validateUserExists(userId);

        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        boolean hasTodayMeasurement = measurementSessionRepository
                .existsByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
                        userId,
                        MeasurementStatus.COMPLETED,
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                );

        return MeasurementResponseDTO.TodayMeasurementStatusResultDTO.builder()
                .today(today)
                .hasTodayMeasurement(hasTodayMeasurement)
                .build();
    }

    @Override
    public MeasurementResponseDTO.WeeklyMeasurementStatusResultDTO getWeeklyMeasurementStatus(Long userId) {
        validateUserExists(userId);

        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        LocalDate weekStartDate = today.minusDays(today.getDayOfWeek().getValue() % 7L);
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        LocalDateTime weekStartDateTime = weekStartDate.atStartOfDay();
        LocalDateTime weekEndExclusiveDateTime = weekEndDate.plusDays(1).atStartOfDay();

        Set<LocalDate> measuredDates = measurementSessionRepository
                .findByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
                        userId,
                        MeasurementStatus.COMPLETED,
                        weekStartDateTime,
                        weekEndExclusiveDateTime
                )
                .stream()
                .map(MeasurementSession::getMeasuredAt)
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.toSet());

        List<MeasurementResponseDTO.DailyMeasurementStatusDTO> dailyStatuses = IntStream.rangeClosed(0, 6)
                .mapToObj(weekStartDate::plusDays)
                .map(date -> MeasurementResponseDTO.DailyMeasurementStatusDTO.builder()
                        .date(date)
                        .dayOfWeek(date.getDayOfWeek().name())
                        .dayOfWeekKor(toKoreanDayOfWeek(date.getDayOfWeek()))
                        .hasMeasurement(measuredDates.contains(date))
                        .build())
                .toList();

        return MeasurementResponseDTO.WeeklyMeasurementStatusResultDTO.builder()
                .today(today)
                .weekStartDate(weekStartDate)
                .weekEndDate(weekEndDate)
                .hasWeeklyMeasurement(!measuredDates.isEmpty())
                .dailyStatuses(dailyStatuses)
                .build();
    }

    private String toKoreanDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SUNDAY -> "일";
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
        };
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserHandler(ErrorStatus.USER_NOT_FOUND);
        }
    }
}
