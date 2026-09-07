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
class MeasurementRecaptureTest {
    @Mock MeasurementSessionRepository sessions;
    @Mock UserRepository users;
    @Mock MeasurementSocketService socket;
    @Mock MeasurementHardwareClient hardware;
    @Mock MeasurementCompletionService completion;
    @Mock EntityManager entityManager;
    @InjectMocks MeasurementCommandServiceImpl service;

    private MeasurementSession session;

    @BeforeEach
    void setUp() {
        session = MeasurementSession.builder().id(79L).user(User.builder().id(6L).build())
                .status(CAPTURING_PHOTO).measuredAt(LocalDateTime.now().minusMinutes(2)).build();
        when(sessions.findByIdForUpdate(79L)).thenReturn(Optional.of(session));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void footOccludingMarkerWaitsForRecaptureInsteadOfFailing() {
        var result = update(WAITING_FOR_RECAPTURE, null, null);

        assertThat(result.getStatus()).isEqualTo(WAITING_FOR_RECAPTURE);
        assertThat(result.getDetail()).contains("발이 기준 ArUco 마커를 가리고", "발 위치를 조정");
        assertThat(result.getFailureReason()).isNull();
        assertThat(result.getFailureDetail()).isNull();
        assertThat(result.getPhotoCaptureAttempt()).isZero();
        assertThat(result.getRemainingPhotoRecaptures()).isEqualTo(3);
        verify(completion).resetPhotoAnalysisForRecapture(session);
        verify(socket).sendMeasurementStatusChanged(session);
        verifyNoInteractions(hardware);
    }

    @Test
    void readyRecaptureIncrementsOnceAndDispatchesOnlyPhotoAfterCommit() {
        update(WAITING_FOR_RECAPTURE, null, 0);
        var result = update(READY_FOR_RECAPTURE, null, null);
        update(READY_FOR_RECAPTURE, null, null);

        assertThat(result.getPhotoCaptureAttempt()).isOne();
        assertThat(result.getRemainingPhotoRecaptures()).isEqualTo(2);
        assertThat(session.getRecaptureDetail()).isNull();
        assertThat(session.getId()).isEqualTo(79L);
        verifyNoInteractions(hardware);
        commitCallbacks();
        verify(hardware).requestPhotoCapture(79L, "Bearer test", 1);
        verifyNoMoreInteractions(hardware);
    }

    @Test
    void duplicateValidationFailureDoesNotConsumeRetriesOrEmitDuplicateEvents() {
        update(WAITING_FOR_RECAPTURE, null, 0);
        update(WAITING_FOR_RECAPTURE, null, 0);
        update(WAITING_FOR_RECAPTURE, MeasurementFailureReason.INVALID_CAPTURE_DATA, 0);

        assertThat(session.getPhotoRecaptureCount()).isZero();
        verify(socket, times(1)).sendMeasurementStatusChanged(session);
        verify(completion, times(1)).resetPhotoAnalysisForRecapture(session);
    }

    @Test
    void failsOnlyAfterThreeRecapturesAlsoFail() {
        update(WAITING_FOR_RECAPTURE, null, 0);
        for (int attempt = 1; attempt <= 3; attempt++) {
            update(READY_FOR_RECAPTURE, null, attempt - 1);
            update(CAPTURING_PHOTO, null, attempt);
            var result = update(WAITING_FOR_RECAPTURE, null, attempt);
            assertThat(result.getPhotoCaptureAttempt()).isEqualTo(attempt);
            assertThat(result.getStatus()).isEqualTo(attempt < 3 ? WAITING_FOR_RECAPTURE : FAILED);
        }

        assertThat(session.getFailureReason()).isEqualTo(MeasurementFailureReason.INVALID_CAPTURE_DATA);
        assertThat(session.getFailureDetail()).contains("반복적으로", "3회");
        assertThat(session.getMeasurementDurationSec()).isPositive();
        assertThat(session.getRecaptureDetail()).isNull();
        commitCallbacks();
        verify(hardware).requestPhotoCapture(79L, "Bearer test", 1);
        verify(hardware).requestPhotoCapture(79L, "Bearer test", 2);
        verify(hardware).requestPhotoCapture(79L, "Bearer test", 3);
        verifyNoMoreInteractions(hardware);
    }

    @Test
    void successfulRecaptureContinuesToEnvironmentInSameSession() {
        update(WAITING_FOR_RECAPTURE, null, 0);
        update(READY_FOR_RECAPTURE, null, 0);
        update(CAPTURING_PHOTO, null, 1);
        var result = update(WAITING_FOR_ENVIRONMENT, null, 1);

        assertThat(result.getId()).isEqualTo(79L);
        assertThat(result.getStatus()).isEqualTo(WAITING_FOR_ENVIRONMENT);
        assertThat(result.getFailureReason()).isNull();
        verify(completion).refreshCaptureCompletedByStatus(session, WAITING_FOR_ENVIRONMENT);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"CAPTURING_PHOTO", "WAITING_FOR_ENVIRONMENT", "WAITING_FOR_RECAPTURE", "FAILED"})
    void staleAndMissingRetryCallbackAttemptsAreRejected(MeasurementStatus status) {
        update(WAITING_FOR_RECAPTURE, null, 0);
        update(READY_FOR_RECAPTURE, null, 0);
        update(CAPTURING_PHOTO, null, 1);
        for (Integer attempt : new Integer[]{null, 0, 2}) {
            assertError(() -> update(status, MeasurementFailureReason.INVALID_CAPTURE_DATA, attempt),
                    ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);
        }
        assertThat(session.getStatus()).isEqualTo(CAPTURING_PHOTO);
        assertThat(session.getPhotoRecaptureCount()).isOne();
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"CAPTURING_PHOTO", "READY_FOR_PHOTO", "WAITING_FOR_ENVIRONMENT", "ANALYZING", "COMPLETED"})
    void cannotSkipRetryReadiness(MeasurementStatus next) {
        update(WAITING_FOR_RECAPTURE, null, 0);
        assertError(() -> update(next, null, 0), ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertThat(session.getStatus()).isEqualTo(WAITING_FOR_RECAPTURE);
    }

    @Test
    void oldStartCallbackCannotRewindSuccessfulRecapture() {
        update(WAITING_FOR_RECAPTURE, null, 0);
        update(READY_FOR_RECAPTURE, null, 0);
        update(CAPTURING_PHOTO, null, 1);
        update(WAITING_FOR_ENVIRONMENT, null, 1);

        assertError(() -> update(CAPTURING_PHOTO, null, 1), ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertError(() -> update(WAITING_FOR_RECAPTURE, null, 1), ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertThat(session.getStatus()).isEqualTo(WAITING_FOR_ENVIRONMENT);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementFailureReason.class, names = {"INVALID_CAPTURE_DATA", "CAMERA_ERROR", "AI_SERVER_ERROR", "NETWORK_ERROR", "USER_CANCELLED"})
    void unrelatedErrorsStillFailImmediately(MeasurementFailureReason reason) {
        assertThat(update(FAILED, reason, null).getStatus()).isEqualTo(FAILED);
        assertThat(session.getFailureReason()).isEqualTo(reason);
        verify(completion, never()).resetPhotoAnalysisForRecapture(any());
    }

    @Test
    void unrelatedFailureReasonCannotBeReportedAsMarkerOcclusion() {
        assertError(() -> update(WAITING_FOR_RECAPTURE, MeasurementFailureReason.CAMERA_ERROR, null),
                ErrorStatus._BAD_REQUEST);
        assertThat(session.getStatus()).isEqualTo(CAPTURING_PHOTO);
        verifyNoInteractions(socket, completion, hardware);
    }

    @Test
    void terminalSessionsCannotBeResurrected() {
        session.updateStatus(COMPLETED, 180);
        assertError(() -> update(WAITING_FOR_RECAPTURE, null, 0), ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertError(() -> update(FAILED, MeasurementFailureReason.CAMERA_ERROR, 0), ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        session.updateStatus(FAILED, 180);
        assertError(() -> update(READY_FOR_RECAPTURE, null, 0), ErrorStatus.MEASUREMENT_ALREADY_FAILED);
    }

    @Test
    void cannotRetryAnotherUsersSession() {
        assertError(() -> service.updateMeasurementStatus(7L, 79L,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(WAITING_FOR_RECAPTURE, null), "Bearer test"),
                ErrorStatus.MEASUREMENT_FORBIDDEN);
        assertThat(session.getStatus()).isEqualTo(CAPTURING_PHOTO);
        verifyNoInteractions(hardware, socket, completion);
    }

    private MeasurementResponseDTO.UpdateMeasurementStatusResultDTO update(MeasurementStatus status,
            MeasurementFailureReason reason, Integer attempt) {
        return service.updateMeasurementStatus(6L, 79L,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(status, null, reason, null, attempt), "Bearer test");
    }

    private void commitCallbacks() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void assertError(Runnable action, ErrorStatus code) {
        assertThatThrownBy(action::run).isInstanceOf(MeasurementHandler.class)
                .satisfies(error -> assertThat(((MeasurementHandler) error).getCode()).isEqualTo(code));
    }
}
