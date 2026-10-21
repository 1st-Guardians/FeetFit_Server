package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.*;
import com.feetfit.server.event.MeasurementHardwareFailureEvent;
import com.feetfit.server.event.MeasurementPressureStatusFailureEvent;
import com.feetfit.server.repository.*;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "measurement.pressure.watchdog-enabled=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MeasurementCommandServiceImpl.class, MeasurementPressureRecoveryService.class,
        MeasurementCompletionService.class, MeasurementSocketService.class, MeasurementPressureWatchdog.class})
class MeasurementPressureRecoveryJpaIntegrationTest {
    @Autowired MeasurementCommandService commands;
    @Autowired MeasurementPressureRecoveryService recovery;
    @Autowired MeasurementPressureWatchdog watchdog;
    @SpyBean MeasurementCompletionService completion;
    @Autowired MeasurementSessionRepository sessions;
    @Autowired MeasurementAnalysisStatusRepository flags;
    @Autowired DailyFootAnalysisRepository analyses;
    @Autowired ReportRepository reports;
    @Autowired MetricAnalysisResultRepository metrics;
    @Autowired ApplicationEventPublisher events;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean MeasurementHardwareClient hardware;
    @MockBean MeasurementCareTipsGenerationService careTips;
    @MockBean SimpMessagingTemplate broker;
    private TransactionTemplate transactions;
    private Long userId;
    private Long sessionId;
    private Long reportId;

    @BeforeEach
    void setup() {
        transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(ignored -> {
            User user = User.builder().nickname("pressure-recovery").socialType(SocialType.KAKAO)
                    .socialId(UUID.randomUUID().toString()).build();
            em.persist(user);
            Device device = Device.builder().deviceName("pressure-recovery-" + UUID.randomUUID()).build();
            em.persist(device);
            MeasurementSession session = MeasurementSession.builder().user(user).device(device)
                    .measuredAt(LocalDateTime.now().minusMinutes(1)).status(MeasurementStatus.READY_FOR_PRESSURE).build();
            em.persist(session);
            userId = user.getId();
            sessionId = session.getId();
            em.persist(MeasurementAnalysisStatus.builder().measurementSession(session)
                    .photoCaptureCompleted(true).photoAnalysisCompleted(true).environmentAnalysisCompleted(true)
                    .pressureCaptureCompleted(true).pressureAnalysisCompleted(true).metricReportCompleted(true).build());
            em.persist(DailyFootAnalysis.builder().measurementSession(session)
                    .measuredLeftFootSizeMm(250f).measuredRightFootSizeMm(251f).leftFootWidthMm(95f).rightFootWidthMm(96f)
                    .leftPlantarFootprintImageUrl("left-plantar").rightPlantarFootprintImageUrl("right-plantar")
                    .plantarFootprintAnalysisText("Photo analysis")
                    .avgTemperatureCelsius(25f).avgHumidityPercent(50f)
                    .balanceScore(80f).balanceComment("Old balance")
                    .leftPressurePercent(55f).rightPressurePercent(45f)
                    .leftPressureImageUrl("left-pressure").rightPressureImageUrl("right-pressure")
                    .leftPressureHeatmapImageUrl("left-heatmap").rightPressureHeatmapImageUrl("right-heatmap").build());
            for (FootSide side : FootSide.values()) {
                PressureSensorReading reading = PressureSensorReading.builder().measurementSession(session)
                        .footSide(side).recordedAt(LocalDateTime.now()).build();
                for (int i = 0; i < 12; i++) reading.addSensorValue(i, 100f);
                em.persist(reading);
            }
            Report report = Report.builder().measurementSession(session).user(user)
                    .reportDate(LocalDateTime.now()).totalScore(90).build();
            em.persist(report);
            reportId = report.getId();
            for (MetricType metric : List.of(MetricType.PRESSURE_BALANCE, MetricType.HALLUX_VALGUS,
                    MetricType.ATHLETES_FOOT, MetricType.SKIN_IRRITATION, MetricType.FOOT_ENVIRONMENT)) {
                em.persist(MetricAnalysisResult.builder().report(report).metricType(metric).score(90f)
                        .status(GaugeStatus.VERY_GOOD).advice(List.of("Advice")).build());
            }
        });
    }

    @Test
    void hardwareFailureCommitsRetryAndKeepsSocketOpenWithoutAutomaticallyActuatingHardware() {
        events.publishEvent(new MeasurementHardwareFailureEvent(sessionId, 0, MeasurementFailureReason.NETWORK_ERROR));
        events.publishEvent(new MeasurementHardwareFailureEvent(sessionId, 0, MeasurementFailureReason.NETWORK_ERROR));

        MeasurementSession session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        assertThat(session.getPressureRetryCount()).isZero();
        assertThat(session.getFailureReason()).isEqualTo(MeasurementFailureReason.NETWORK_ERROR);
        assertThat(session.getPressureRetryDetail()).contains("다시 측정");
        assertPreservedPhotoAndEnvironment();
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
            assertThat(message.getShouldDisconnect()).isFalse();
            assertThat(message.getPressureMeasurementAttempt()).isZero();
            assertThat(message.getRemainingPressureRetries()).isEqualTo(3);
            assertThat(message.getDetail()).contains("다시 측정");
        });
        verifyNoInteractions(hardware);
    }

    @Test
    void backendStatusWriteFailureRollsBackThenCommitsRetry() {
        doThrow(new DataAccessResourceFailureException("status write failed"))
                .when(completion).refreshCaptureCompletedByStatus(any(), eq(MeasurementStatus.MEASURING_PRESSURE));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> commands.updateMeasurementStatus(userId, sessionId,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                        MeasurementStatus.MEASURING_PRESSURE, null, null, null, null, 0), "Bearer test"))
                .hasMessage("status write failed");

        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        assertThat(session.getPressureRetryDetail()).contains("상태를 저장하지 못했어요");
        assertPreservedPhotoAndEnvironment();
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
            assertThat(message.getShouldDisconnect()).isFalse();
        });
    }

    @Test
    void backendInitialStartFailureOffersRetryEvenWithoutHardwareOrAiCallbacks() {
        mutate(session -> session.updateStatus(MeasurementStatus.WAITING_FOR_PRESSURE, null));
        doThrow(new DataAccessResourceFailureException("start state save failed"))
                .when(completion).refreshCaptureCompletedByStatus(any(), eq(MeasurementStatus.READY_FOR_PRESSURE));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> commands.updateMeasurementStatus(userId, sessionId,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(MeasurementStatus.READY_FOR_PRESSURE, null), "Bearer test"))
                .hasMessage("start state save failed");

        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        assertThat(session.getPressureRetryCount()).isZero();
        assertThat(session.getFailureReason()).isEqualTo(MeasurementFailureReason.REPORT_SAVE_ERROR);
        assertPreservedPhotoAndEnvironment();
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
            assertThat(message.getShouldDisconnect()).isFalse();
            assertThat(message.getDetail()).contains("다시 측정");
        });
        verifyNoInteractions(hardware);
    }

    @Test
    void backendRetryButtonFailureKeepsTheRealAttemptAndPublishesRetryAgain() {
        mutate(session -> {
            session.startPressureRetry();
            session.startPressureRetry();
            session.requirePressureRetry("Retry pressure");
            flags.findByMeasurementSessionId(sessionId).orElseThrow().resetPressureAnalysisForRetry();
        });
        doThrow(new DataAccessResourceFailureException("retry preparation failed"))
                .when(completion).invalidatePressureResultsForRetry(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> commands.updateMeasurementStatus(userId, sessionId,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(MeasurementStatus.READY_FOR_PRESSURE_RETRY, null), "Bearer test"))
                .hasMessage("retry preparation failed");

        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        assertThat(session.getPressureRetryCount()).isEqualTo(2);
        assertThat(session.getFailureReason()).isEqualTo(MeasurementFailureReason.REPORT_SAVE_ERROR);
        assertPreservedPhotoAndEnvironment();
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
            assertThat(message.getPressureMeasurementAttempt()).isEqualTo(2);
            assertThat(message.getRemainingPressureRetries()).isOne();
            assertThat(message.getShouldDisconnect()).isFalse();
        });
        verifyNoInteractions(hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"READY_FOR_PRESSURE", "MEASURING_PRESSURE", "ANALYZING", "COMPLETED", "FAILED"})
    void delayedInitialStartFailureCannotResetAnotherRequestThatAlreadyStarted(MeasurementStatus currentStatus) {
        mutate(session -> session.updateStatus(currentStatus, null));

        events.publishEvent(new MeasurementPressureStatusFailureEvent(userId, sessionId, 0, 0,
                MeasurementStatus.READY_FOR_PRESSURE));

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(currentStatus);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void delayedRetryButtonFailureCannotResetANewerAttempt() {
        mutate(session -> {
            session.startPressureRetry();
            session.requirePressureRetry("Retry pressure");
        });

        events.publishEvent(new MeasurementPressureStatusFailureEvent(userId, sessionId, 0, 0,
                MeasurementStatus.READY_FOR_PRESSURE_RETRY));

        assertThat(sessions.findById(sessionId).orElseThrow().getPressureRetryCount()).isOne();
        assertThat(sessions.findById(sessionId).orElseThrow().getFailureReason()).isNull();
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void unauthorizedStartRequestDoesNotTurnAnotherUsersSessionIntoRetry() {
        mutate(session -> session.updateStatus(MeasurementStatus.WAITING_FOR_PRESSURE, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> commands.updateMeasurementStatus(userId + 1, sessionId,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(MeasurementStatus.READY_FOR_PRESSURE, null), "Bearer test"))
                .isInstanceOf(com.feetfit.server.apiPayload.exception.GeneralException.class);

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void userRetryInvalidatesOnlyOldPressureResultsAndCannotCompleteFromThoseResults() {
        recovery.recoverReportFailure(userId, sessionId, 0, 0);
        var ready = commands.updateMeasurementStatus(userId, sessionId,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                        MeasurementStatus.READY_FOR_PRESSURE_RETRY, null, null, null, null, 0), "Bearer test");

        assertThat(ready.getPressureMeasurementAttempt()).isOne();
        assertThat(ready.getFailureReason()).isNull();
        verify(hardware).requestPressureMeasurement(sessionId, "Bearer test", 1);
        verifyNoMoreInteractions(hardware);
        assertPreservedPhotoAndEnvironment();
        DailyFootAnalysis daily = analyses.findByMeasurementSessionId(sessionId).orElseThrow();
        assertThat(daily.getBalanceScore()).isNull();
        assertThat(daily.getLeftPressurePercent()).isNull();
        assertThat(daily.getLeftPressureImageUrl()).isNull();
        assertThat(daily.getLeftPressureHeatmapImageUrl()).isNull();
        assertThat(reports.findById(reportId).orElseThrow().getTotalScore()).isNull();
        assertThat(metrics.findByReportIdAndMetricType(reportId, MetricType.PRESSURE_BALANCE)).isEmpty();
        assertThat(metrics.findByReportId(reportId)).hasSize(4);

        for (MeasurementStatus status : List.of(MeasurementStatus.MEASURING_PRESSURE, MeasurementStatus.ANALYZING)) {
            commands.updateMeasurementStatus(userId, sessionId,
                    new MeasurementRequestDTO.UpdateMeasurementStatusDTO(status, null, null, null, null, 1), "Bearer test");
        }
        transactions.executeWithoutResult(ignored -> {
            MeasurementSession session = sessions.findByIdForUpdate(sessionId).orElseThrow();
            completion.refreshPressureAnalysisCompleted(session);
            completion.refreshMetricReportCompleted(session);
        });
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        assertThat(flags.findByMeasurementSessionId(sessionId).orElseThrow().isReadyToComplete()).isFalse();
        verifyNoInteractions(careTips);

        transactions.executeWithoutResult(ignored -> {
            MeasurementSession session = sessions.findByIdForUpdate(sessionId).orElseThrow();
            DailyFootAnalysis fresh = analyses.findByMeasurementSessionId(sessionId).orElseThrow();
            fresh.updatePressure(50f, 50f, "new-left-pressure", "new-right-pressure");
            fresh.updatePressureHeatmapImages("new-left-heatmap", "new-right-heatmap");
            fresh.updateBalance(95f, "New balance");
            Report report = reports.findById(reportId).orElseThrow();
            report.updateTotalScore(91);
            em.persist(MetricAnalysisResult.builder().report(report).metricType(MetricType.PRESSURE_BALANCE)
                    .score(95f).status(GaugeStatus.VERY_GOOD).advice(List.of("New advice")).build());
            completion.refreshPressureAnalysisCompleted(session);
            completion.refreshMetricReportCompleted(session);
        });
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        assertThat(reports.findByMeasurementSessionId(sessionId).orElseThrow().getId()).isEqualTo(reportId);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"COMPLETED", "FAILED", "WAITING_FOR_RECAPTURE", "WAITING_FOR_PRESSURE"})
    void terminalOrUnrelatedPhaseIsNotReopened(MeasurementStatus status) {
        mutate(session -> session.updateStatus(status, null));

        recovery.recoverReportFailure(userId, sessionId, 0, 0);
        events.publishEvent(new MeasurementHardwareFailureEvent(sessionId, 0, MeasurementFailureReason.NETWORK_ERROR));

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(status);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void oldAttemptOrWrongOwnerCannotResetANewerAttempt() {
        mutate(MeasurementSession::startPressureRetry);
        recovery.recoverReportFailure(userId, sessionId, 0, 0);
        recovery.recoverReportFailure(userId + 1, sessionId, 0, 1);
        events.publishEvent(new MeasurementHardwareFailureEvent(sessionId, 0, MeasurementFailureReason.NETWORK_ERROR));

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.READY_FOR_PRESSURE);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void oldPhotoAttemptCannotResetPressure() {
        mutate(MeasurementSession::startPhotoRecapture);
        recovery.recoverReportFailure(userId, sessionId, 0, 0);

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.READY_FOR_PRESSURE);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void lateHardwareErrorDoesNotInterruptAnAcceptedAnalysisButSaveFailureCanRequestRetry() {
        mutate(session -> session.updateStatus(MeasurementStatus.ANALYZING, null));
        events.publishEvent(new MeasurementHardwareFailureEvent(sessionId, 0, MeasurementFailureReason.NETWORK_ERROR));
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        verifyNoInteractions(broker);

        recovery.recoverReportFailure(userId, sessionId, 0, 0);

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
    }

    @Test
    void exhaustedRetriesFailWithOriginalReasonAndDisconnect() {
        mutate(session -> {
            for (int i = 0; i < 3; i++) session.startPressureRetry();
        });

        recovery.recoverReportFailure(userId, sessionId, 0, 3);

        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.FAILED);
        assertThat(session.getFailureReason()).isEqualTo(MeasurementFailureReason.REPORT_SAVE_ERROR);
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> assertThat(message.getShouldDisconnect()).isTrue());
        verifyNoInteractions(hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"MEASURING_PRESSURE", "ANALYZING"})
    void lateHardwareTimeoutOrErrorDoesNotInterruptStartedWork(MeasurementStatus status) {
        mutate(session -> session.updateStatus(status, null));

        events.publishEvent(new MeasurementHardwareFailureEvent(sessionId, 0, MeasurementFailureReason.HARDWARE_TIMEOUT));
        events.publishEvent(new MeasurementHardwareFailureEvent(sessionId, 0, MeasurementFailureReason.NETWORK_ERROR));

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(status);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void requestTimeoutBeforeStartCallbackWaitsForTheStartDeadline() {
        events.publishEvent(new MeasurementHardwareFailureEvent(sessionId, 0, MeasurementFailureReason.HARDWARE_TIMEOUT));
        watchdog.recoverStalledSessions();

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.READY_FOR_PRESSURE);
        verifyNoInteractions(broker, hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"READY_FOR_PRESSURE", "READY_FOR_PRESSURE_RETRY", "MEASURING_PRESSURE"})
    void measurementStageOffersRetryAtThirtySecondsButNotEarlier(MeasurementStatus status) {
        makeStale(status);
        LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        mutate(session -> ReflectionTestUtils.setField(session, "pressureProgressAt", startedAt));

        watchdog.recoverStalledSessions(startedAt.plusSeconds(29));
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(status);
        verifyNoInteractions(broker, hardware);

        watchdog.recoverStalledSessions(startedAt.plusSeconds(30));

        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        assertThat(session.getPressureRetryCount()).isZero();
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
            assertThat(message.getShouldDisconnect()).isFalse();
        });
        verifyNoInteractions(hardware);
    }

    @Test
    void analysingAndUserPreparationAreNotSubjectToThirtySecondMeasurementLimit() {
        makeStale(MeasurementStatus.ANALYZING);
        LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        mutate(session -> ReflectionTestUtils.setField(session, "pressureProgressAt", startedAt));

        watchdog.recoverStalledSessions(startedAt.plusSeconds(30));
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        mutate(session -> session.updateStatus(MeasurementStatus.WAITING_FOR_PRESSURE, null));
        watchdog.recoverStalledSessions(startedAt.plusHours(1));
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE);
        verifyNoInteractions(broker, hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"READY_FOR_PRESSURE", "READY_FOR_PRESSURE_RETRY", "MEASURING_PRESSURE", "ANALYZING"})
    void lostStartOrAiCallbackEventuallyOffersRetryWithoutRestartingHardware(MeasurementStatus status) {
        makeStale(status);

        watchdog.recoverStalledSessions();
        watchdog.recoverStalledSessions();

        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        assertThat(session.getPressureRetryCount()).isZero();
        assertThat(session.getFailureReason()).isEqualTo(status == MeasurementStatus.ANALYZING
                ? MeasurementFailureReason.AI_SERVER_ERROR : MeasurementFailureReason.HARDWARE_TIMEOUT);
        assertPreservedPhotoAndEnvironment();
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
            assertThat(message.getShouldDisconnect()).isFalse();
            assertThat(message.getDetail()).contains("사진", "다시 측정");
        });
        verifyNoInteractions(hardware);
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"READY_FOR_PRESSURE", "READY_FOR_PRESSURE_RETRY", "MEASURING_PRESSURE", "ANALYZING"})
    void recentProgressIsNotTreatedAsAStall(MeasurementStatus status) {
        makeStale(status);
        mutate(MeasurementSession::recordPressureProgress);

        watchdog.recoverStalledSessions();

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(status);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void completedPressureWaitingForPhotoIsExcludedFromTheWatchdogQuery() {
        mutate(session -> {
            session.updateStatus(MeasurementStatus.ANALYZING, null);
            ReflectionTestUtils.setField(session, "pressureProgressAt", LocalDateTime.now().minusHours(1));
            flags.findByMeasurementSessionId(sessionId).orElseThrow().resetPhotoAnalysisForRecapture();
        });

        var candidates = sessions.findPressureTimeoutCandidates(Set.of(MeasurementStatus.ANALYZING),
                LocalDateTime.now().minusMinutes(10), MetricType.PRESSURE_BALANCE, PageRequest.of(0, 100));
        assertThat(candidates).extracting(MeasurementSessionRepository.PressureTimeoutCandidate::getId).doesNotContain(sessionId);
        watchdog.recoverStalledSessions();

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void refreshedProgressAfterCandidateSelectionPreventsRecovery() {
        makeStale(MeasurementStatus.MEASURING_PRESSURE);
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(3);
        mutate(MeasurementSession::recordPressureProgress);

        recovery.recoverStalledPressure(sessionId, 0, MeasurementStatus.MEASURING_PRESSURE, staleBefore);

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void phaseChangeAfterCandidateSelectionPreventsRecovery() {
        makeStale(MeasurementStatus.MEASURING_PRESSURE);
        mutate(session -> session.updateStatus(MeasurementStatus.ANALYZING, null));

        recovery.recoverStalledPressure(sessionId, 0, MeasurementStatus.MEASURING_PRESSURE, LocalDateTime.now());

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void newAttemptAfterCandidateSelectionCannotBeResetByAnOldTimeout() {
        makeStale(MeasurementStatus.MEASURING_PRESSURE);
        mutate(MeasurementSession::startPressureRetry);

        recovery.recoverStalledPressure(sessionId, 0, MeasurementStatus.MEASURING_PRESSURE, LocalDateTime.now());

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void completedPressureAfterCandidateSelectionIsRecheckedUnderTheLock() {
        makeStale(MeasurementStatus.ANALYZING);
        mutate(session -> {
            var status = flags.findByMeasurementSessionId(sessionId).orElseThrow();
            status.completePressureCapture();
            status.completePressureAnalysis();
        });

        recovery.recoverStalledPressure(sessionId, 0, MeasurementStatus.ANALYZING, LocalDateTime.now());

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        verifyNoInteractions(broker, hardware);
    }

    @Test
    void duplicateMeasuringCallbackDoesNotExtendTheProgressDeadline() {
        makeStale(MeasurementStatus.MEASURING_PRESSURE);
        var lastProgress = sessions.findById(sessionId).orElseThrow().getPressureProgressAt();

        commands.updateMeasurementStatus(userId, sessionId, new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                MeasurementStatus.MEASURING_PRESSURE, null, null, null, null, 0), "Bearer test");

        assertThat(sessions.findById(sessionId).orElseThrow().getPressureProgressAt()).isEqualTo(lastProgress);
        clearInvocations(broker);
        watchdog.recoverStalledSessions();
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        verifyNoInteractions(hardware);
    }

    @Test
    void legacySessionWithoutProgressTimestampCanAlsoRecover() {
        makeStale(MeasurementStatus.MEASURING_PRESSURE);
        transactions.executeWithoutResult(ignored -> em.createNativeQuery("""
                update measurement_session set pressure_progress_at = null, updated_at = :old where id = :id
                """).setParameter("old", LocalDateTime.now().minusHours(1)).setParameter("id", sessionId).executeUpdate());

        watchdog.recoverStalledSessions();

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
    }

    @Test
    void lateOldCallbackIsRejectedAndUserRetryStartsOneNewAttempt() {
        makeStale(MeasurementStatus.MEASURING_PRESSURE);
        watchdog.recoverStalledSessions();

        var oldCallback = new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                MeasurementStatus.MEASURING_PRESSURE, null, null, null, null, 0);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> commands.updateMeasurementStatus(
                userId, sessionId, oldCallback, "Bearer test"))
                .isInstanceOf(com.feetfit.server.apiPayload.exception.GeneralException.class);

        commands.updateMeasurementStatus(userId, sessionId, new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                MeasurementStatus.READY_FOR_PRESSURE_RETRY, null, null, null, null, 0), "Bearer test");
        verify(hardware).requestPressureMeasurement(sessionId, "Bearer test", 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> commands.updateMeasurementStatus(
                userId, sessionId, oldCallback, "Bearer test"))
                .isInstanceOf(com.feetfit.server.apiPayload.exception.GeneralException.class);
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.READY_FOR_PRESSURE_RETRY);
        assertThat(sessions.findById(sessionId).orElseThrow().getPressureRetryCount()).isOne();
        verifyNoMoreInteractions(hardware);
    }

    @Test
    void stalledFinalAttemptFailsRatherThanOfferingUnlimitedRetries() {
        makeStale(MeasurementStatus.MEASURING_PRESSURE);
        mutate(session -> {
            for (int i = 0; i < 3; i++) session.startPressureRetry();
        });

        watchdog.recoverStalledSessions();

        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.FAILED);
        assertThat(session.getFailureReason()).isEqualTo(MeasurementFailureReason.HARDWARE_TIMEOUT);
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> assertThat(message.getShouldDisconnect()).isTrue());
        verifyNoInteractions(hardware);
    }

    private void makeStale(MeasurementStatus status) {
        mutate(session -> {
            session.updateStatus(status, null);
            ReflectionTestUtils.setField(session, "pressureProgressAt", LocalDateTime.now().minusHours(1));
            flags.findByMeasurementSessionId(sessionId).orElseThrow().resetPressureAnalysisForRetry();
        });
    }

    private void assertPreservedPhotoAndEnvironment() {
        var status = flags.findByMeasurementSessionId(sessionId).orElseThrow();
        assertThat(status.isPhotoCaptureCompleted()).isTrue();
        assertThat(status.isPhotoAnalysisCompleted()).isTrue();
        assertThat(status.isEnvironmentAnalysisCompleted()).isTrue();
        assertThat(status.isPressureAnalysisCompleted()).isFalse();
        assertThat(status.isMetricReportCompleted()).isFalse();
        var analysis = analyses.findByMeasurementSessionId(sessionId).orElseThrow();
        assertThat(analysis.getMeasuredLeftFootSizeMm()).isEqualTo(250f);
        assertThat(analysis.getLeftPlantarFootprintImageUrl()).isEqualTo("left-plantar");
        assertThat(analysis.getAvgTemperatureCelsius()).isEqualTo(25f);
    }

    private void mutate(Consumer<MeasurementSession> change) {
        transactions.executeWithoutResult(ignored -> change.accept(sessions.findByIdForUpdate(sessionId).orElseThrow()));
    }
}
