package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementFailureReason;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.feetfit.server.domain.enums.MeasurementStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasurementPressureRetryTest {
    @Mock MeasurementSessionRepository sessions;
    @Mock UserRepository users;
    @Mock MeasurementSocketService socket;
    @Mock MeasurementHardwareClient hardware;
    @Mock MeasurementCompletionService completion;
    @Mock EntityManager entityManager;
    @Mock MeasurementWriteTransactions writeTransactions;
    @InjectMocks MeasurementCommandServiceImpl service;

    private MeasurementSession session;

    @BeforeEach
    void setUp() {
        lenient().when(writeTransactions.execute(any(), any()))
                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        lenient().when(writeTransactions.executeRequired(any()))
                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(0)).get());
        session = MeasurementSession.builder().id(166L).user(User.builder().id(6L).build())
                .status(MEASURING_PRESSURE).measuredAt(LocalDateTime.now().minusMinutes(2)).build();
        lenient().when(sessions.findByIdForUpdate(166L)).thenReturn(Optional.of(session));
        lenient().when(sessions.findById(166L)).thenReturn(Optional.of(session));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void invalidPressureWaitsForRetryInsteadOfFailing() {
        var result = update(WAITING_FOR_PRESSURE_RETRY, MeasurementFailureReason.INVALID_PRESSURE_DATA, 0);

        assertThat(result.getStatus()).isEqualTo(WAITING_FOR_PRESSURE_RETRY);
        assertThat(result.getDetail()).contains("압력값", "다시 측정");
        assertThat(result.getFailureReason()).isNull();
        assertThat(result.getFailureDetail()).isNull();
        assertThat(result.getPressureMeasurementAttempt()).isZero();
        assertThat(result.getRemainingPressureRetries()).isEqualTo(3);
        verify(completion).resetPressureAnalysisForRetry(session);
        verify(socket).sendMeasurementStatusChanged(session);
        verifyNoInteractions(hardware);
    }

    @Test
    void readyPressureRetryIncrementsOnceAndDispatchesPressureAfterCommit() {
        update(WAITING_FOR_PRESSURE_RETRY, null, 0);
        var result = update(READY_FOR_PRESSURE_RETRY, null, null);
        update(READY_FOR_PRESSURE_RETRY, null, null);

        assertThat(result.getPressureMeasurementAttempt()).isOne();
        assertThat(result.getRemainingPressureRetries()).isEqualTo(2);
        assertThat(session.getPressureRetryDetail()).isNull();
        verifyNoInteractions(hardware);

        commitCallbacks();

        verify(hardware).requestPressureMeasurement(166L, "Bearer test", 1);
        verifyNoMoreInteractions(hardware);
    }

    @Test
    void successfulPressureRetryContinuesToAnalyzingInSameSession() {
        update(WAITING_FOR_PRESSURE_RETRY, null, 0);
        update(READY_FOR_PRESSURE_RETRY, null, 0);
        update(MEASURING_PRESSURE, null, 1);
        var result = update(ANALYZING, null, 1);

        assertThat(result.getId()).isEqualTo(166L);
        assertThat(result.getStatus()).isEqualTo(ANALYZING);
        assertThat(result.getFailureReason()).isNull();
        verify(completion).refreshCaptureCompletedByStatus(session, ANALYZING);
    }

    @Test
    void failsOnlyAfterThreePressureRetriesAlsoFail() {
        update(WAITING_FOR_PRESSURE_RETRY, null, 0);
        for (int attempt = 1; attempt <= 3; attempt++) {
            update(READY_FOR_PRESSURE_RETRY, null, attempt - 1);
            update(MEASURING_PRESSURE, null, attempt);
            var result = update(WAITING_FOR_PRESSURE_RETRY, null, attempt);
            assertThat(result.getPressureMeasurementAttempt()).isEqualTo(attempt);
            assertThat(result.getStatus()).isEqualTo(attempt < 3 ? WAITING_FOR_PRESSURE_RETRY : FAILED);
        }

        assertThat(session.getFailureReason()).isEqualTo(MeasurementFailureReason.INVALID_PRESSURE_DATA);
        assertThat(session.getFailureDetail()).contains("반복적으로", "3회");
        assertThat(session.getMeasurementDurationSec()).isPositive();
        assertThat(session.getPressureRetryDetail()).isNull();
        commitCallbacks();
        verify(hardware).requestPressureMeasurement(166L, "Bearer test", 1);
        verify(hardware).requestPressureMeasurement(166L, "Bearer test", 2);
        verify(hardware).requestPressureMeasurement(166L, "Bearer test", 3);
        verifyNoMoreInteractions(hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"MEASURING_PRESSURE", "WAITING_FOR_PRESSURE_RETRY", "ANALYZING", "FAILED"})
    void staleAndMissingPressureRetryCallbackAttemptsAreRejected(MeasurementStatus status) {
        update(WAITING_FOR_PRESSURE_RETRY, null, 0);
        update(READY_FOR_PRESSURE_RETRY, null, 0);
        update(MEASURING_PRESSURE, null, 1);

        for (Integer attempt : new Integer[]{null, 0, 2}) {
            assertError(() -> update(status, MeasurementFailureReason.INVALID_PRESSURE_DATA, attempt),
                    ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);
        }
        assertThat(session.getStatus()).isEqualTo(MEASURING_PRESSURE);
        assertThat(session.getPressureRetryCount()).isOne();
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementFailureReason.class, names = {"CAMERA_ERROR", "AI_SERVER_ERROR", "NETWORK_ERROR", "USER_CANCELLED", "PRESSURE_SENSOR_ERROR"})
    void unrelatedErrorsStillFailImmediately(MeasurementFailureReason reason) {
        assertThat(update(FAILED, reason, null).getStatus()).isEqualTo(FAILED);
        assertThat(session.getFailureReason()).isEqualTo(reason);
        verify(completion, never()).resetPressureAnalysisForRetry(any());
    }

    @Test
    void unrelatedFailureReasonCannotBeReportedAsPressureRetry() {
        assertError(() -> update(WAITING_FOR_PRESSURE_RETRY, MeasurementFailureReason.PRESSURE_SENSOR_ERROR, null),
                ErrorStatus._BAD_REQUEST);
        assertThat(session.getStatus()).isEqualTo(MEASURING_PRESSURE);
        verifyNoInteractions(socket, completion, hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"READY_FOR_PRESSURE", "READY_FOR_PRESSURE_RETRY"})
    void startingPressureOnlyUpdatesTheStateWithoutRunningAnalysisChecks(MeasurementStatus readyStatus) {
        session.updateStatus(readyStatus, null);
        if (readyStatus == READY_FOR_PRESSURE_RETRY) session.startPressureRetry();

        var response = update(MEASURING_PRESSURE, null, session.getPressureRetryCount());

        assertThat(response.getStatus()).isEqualTo(MEASURING_PRESSURE);
        assertThat(response.getPressureMeasurementAttempt()).isEqualTo(session.getPressureRetryCount());
        verify(sessions).findByIdForUpdate(166L);
        verify(socket).sendMeasurementStatusChanged(session);
        verifyNoInteractions(completion, hardware);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 2})
    void firstMeasuringCallbackStillRejectsMissingOrWrongRetryAttempts(Integer attempt) {
        session.updateStatus(READY_FOR_PRESSURE_RETRY, null);
        session.startPressureRetry();

        assertError(() -> update(MEASURING_PRESSURE, null, attempt), ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);

        assertThat(session.getStatus()).isEqualTo(READY_FOR_PRESSURE_RETRY);
        assertThat(session.getPressureRetryCount()).isEqualTo(1);
        verifyNoInteractions(completion, socket, hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"MEASURING_PRESSURE", "ANALYZING"})
    void duplicatePressureProgressDoesNotRecomputeOrPublish(MeasurementStatus status) {
        session.updateStatus(status, null);
        assertThat(update(status, null, 0).getStatus()).isEqualTo(status);
        verify(sessions, never()).findByIdForUpdate(any());
        verifyNoInteractions(socket, completion, hardware, writeTransactions);
    }

    @Test
    void unchangedStatusStillRejectsAnotherUserWithoutTakingAWriteLock() {
        assertError(() -> service.updateMeasurementStatus(7L, 166L,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(MEASURING_PRESSURE, null, null, null, null, 0),
                "Bearer test"), ErrorStatus.MEASUREMENT_FORBIDDEN);
        verify(sessions, never()).findByIdForUpdate(any());
        verifyNoInteractions(socket, completion, hardware, writeTransactions);
    }

    @Test
    void unchangedStatusStillRejectsMissingSession() {
        when(sessions.findById(166L)).thenReturn(Optional.empty());
        assertError(() -> update(MEASURING_PRESSURE, null, 0), ErrorStatus.MEASUREMENT_NOT_FOUND);
        verify(sessions, never()).findByIdForUpdate(any());
        verifyNoInteractions(writeTransactions);
    }

    @Test
    void unchangedStatusStillRejectsNegativeAttemptWithoutTakingAWriteLock() {
        assertError(() -> update(MEASURING_PRESSURE, null, -1), ErrorStatus._BAD_REQUEST);
        verify(sessions, never()).findByIdForUpdate(any());
        verifyNoInteractions(writeTransactions);
    }

    @Test
    void sameStatusWithANewDurationUsesTheWriteTransaction() {
        var response = service.updateMeasurementStatus(6L, 166L,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(MEASURING_PRESSURE, 60, null, null, null, 0),
                "Bearer test");
        assertThat(response.getMeasurementDurationSec()).isEqualTo(60);
        verify(writeTransactions).execute(eq(166L), any());
        verify(sessions).findByIdForUpdate(166L);
    }

    @Test
    void delayedMeasuringCallbackCannotRegressAnalyzing() {
        session.updateStatus(ANALYZING, null);
        assertError(() -> update(MEASURING_PRESSURE, null, 0), ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertThat(session.getStatus()).isEqualTo(ANALYZING);
        verifyNoInteractions(socket, completion, hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"MEASURING_PRESSURE", "ANALYZING"})
    void lateInitialReadyButtonCannotStartTheHardwareAgain(MeasurementStatus currentStatus) {
        session.updateStatus(currentStatus, null);
        assertError(() -> update(READY_FOR_PRESSURE, null, 0), ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertThat(session.getStatus()).isEqualTo(currentStatus);
        verifyNoInteractions(socket, completion, hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"WAITING_FOR_PHOTO_ENVIRONMENT", "CAPTURING_PHOTO_ENVIRONMENT", "WAITING_FOR_PRESSURE"})
    void measuringCannotSkipPressureReadiness(MeasurementStatus currentStatus) {
        session.updateStatus(currentStatus, null);
        assertError(() -> update(MEASURING_PRESSURE, null, 0), ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertThat(session.getStatus()).isEqualTo(currentStatus);
        verifyNoInteractions(socket, completion, hardware);
    }

    private MeasurementResponseDTO.UpdateMeasurementStatusResultDTO update(MeasurementStatus status,
            MeasurementFailureReason reason, Integer attempt) {
        return service.updateMeasurementStatus(6L, 166L,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(status, null, reason, null, null, attempt),
                "Bearer test");
    }

    private void commitCallbacks() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void assertError(Runnable action, ErrorStatus code) {
        assertThatThrownBy(action::run).isInstanceOf(MeasurementHandler.class)
                .satisfies(error -> assertThat(((MeasurementHandler) error).getCode()).isEqualTo(code));
    }
}
