package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.repository.*;
import com.feetfit.server.service.ImageService.ImageUploadService;
import com.feetfit.server.service.MeasurementService.MeasurementCommandService;
import com.feetfit.server.service.MeasurementService.MeasurementCommandServiceImpl;
import com.feetfit.server.service.MeasurementService.MeasurementCompletionService;
import com.feetfit.server.service.MeasurementService.MeasurementHardwareClient;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.service.MeasurementService.MeasurementPressureRecoveryService;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ReportCommandServiceImpl.class, MeasurementCommandServiceImpl.class, MeasurementSocketService.class,
        MeasurementPressureRecoveryService.class})
class ReportImageWriteConcurrencyJpaIntegrationTest {
    @Autowired ReportCommandService reports;
    @Autowired MeasurementCommandService measurements;
    @Autowired MeasurementSocketService sockets;
    @Autowired MeasurementSessionRepository sessions;
    @SpyBean DailyFootAnalysisRepository analyses;
    @Autowired ReportRepository reportRepository;
    @Autowired HalluxValgusAnalysisRepository hallux;
    @Autowired TinaPedisAnalysisRepository tinea;
    @Autowired PressureSensorReadingRepository readings;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockBean ImageUploadService images;
    @MockBean MeasurementCompletionService completion;
    @MockBean MeasurementHardwareClient hardware;
    @MockBean SimpMessagingTemplate broker;
    @MockBean MetricInsightGenerationService insight;

    private TransactionTemplate transactions;
    private Long userId;
    private Long sessionId;

    @BeforeEach
    void setup() {
        transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(ignored -> {
            User user = User.builder().nickname("image-write")
                    .socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).footSize(250).build();
            em.persist(user);
            Device device = Device.builder().deviceName("image-write-" + UUID.randomUUID()).build();
            em.persist(device);
            MeasurementSession session = MeasurementSession.builder().user(user).device(device)
                    .measuredAt(LocalDateTime.now()).status(MeasurementStatus.READY_FOR_PRESSURE).build();
            em.persist(session);
            userId = user.getId();
            sessionId = session.getId();
        });
        when(images.upload(anyString(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return uploaded(call.getArgument(0));
        });
    }

    @ParameterizedTest
    @EnumSource(ImagePart.class)
    void everyImageEndpointUploadsOutsideTransactionsAndCommitsItsResults(ImagePart part) {
        save(part);

        assertSaved(part);
        verify(images, times(part == ImagePart.TINEA ? 4 : 2)).upload(anyString(), any());
        var progressAt = sessions.findById(sessionId).orElseThrow().getPressureProgressAt();
        if (part == ImagePart.PRESSURE || part == ImagePart.HEATMAP) {
            assertThat(progressAt).isNotNull();
        } else {
            assertThat(progressAt).isNull();
        }
        verifyNoInteractions(hardware, broker);
    }

    @Test
    void callerTransactionIsSuspendedForUploads() {
        transactions.executeWithoutResult(ignored -> {
            save(ImagePart.HALLUX);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });
        assertSaved(ImagePart.HALLUX);
    }

    @Test
    void pressureStatusPatchCanCommitBeforePhotoUploadsFinish() throws Exception {
        CountDownLatch uploading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockUploads(uploading, release);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> photoSave = workers.submit(() -> save(ImagePart.TINEA));
            assertThat(uploading.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> callback = workers.submit(() -> measurements.updateMeasurementStatus(userId, sessionId,
                    new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                            MeasurementStatus.MEASURING_PRESSURE, null, null, null, null, 0), "Bearer test"));

            callback.get(3, TimeUnit.SECONDS);
            assertThat(photoSave.isDone()).isFalse();
            assertThat(sessions.findById(sessionId).orElseThrow().getStatus())
                    .isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
            release.countDown();
            photoSave.get(5, TimeUnit.SECONDS);
            assertSaved(ImagePart.TINEA);
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest
    @EnumSource(value = MetricType.class, names = {"ATHLETES_FOOT", "PRESSURE_BALANCE"})
    void pressureStatusPatchCanCommitBeforeMetricTextGenerationFinishes(MetricType metricType) throws Exception {
        CountDownLatch generating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(insight.generate(any(), any(), any(), any(), any())).thenAnswer(call -> {
            generating.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return new MetricInsightGenerationService.MetricInsight(call.getArgument(4), call.getArgument(3));
        });
        var request = metricRequest(metricType);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> summarySave = workers.submit(() -> reports.saveMetricResult(userId, request));
            assertThat(generating.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> callback = workers.submit(() -> measurements.updateMeasurementStatus(userId, sessionId,
                    new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                            MeasurementStatus.MEASURING_PRESSURE, null, null, null, null, 0), "Bearer test"));

            callback.get(3, TimeUnit.SECONDS);
            assertThat(summarySave.isDone()).isFalse();
            release.countDown();
            summarySave.get(5, TimeUnit.SECONDS);
            assertThat(reportRepository.findByMeasurementSessionId(sessionId)).isPresent();
            assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @Test
    void pressureSummaryRechecksTheAttemptAfterSlowTextGeneration() {
        when(insight.generate(any(), any(), any(), any(), any())).thenAnswer(call -> {
            mutateInNewTransaction(MeasurementSession::startPressureRetry);
            return new MetricInsightGenerationService.MetricInsight(call.getArgument(4), call.getArgument(3));
        });

        assertThatThrownBy(() -> reports.saveMetricResult(userId, metricRequest(MetricType.PRESSURE_BALANCE)))
                .isInstanceOf(MeasurementHandler.class)
                .satisfies(error -> assertThat(((MeasurementHandler) error).getCode())
                        .isEqualTo(ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE));

        assertThat(reportRepository.findByMeasurementSessionId(sessionId)).isEmpty();
        assertThat(sessions.findById(sessionId).orElseThrow().getPressureRetryCount()).isOne();
        verifyNoInteractions(broker, completion);
    }

    @Test
    void metricSummaryRechecksFailureStatusAfterSlowTextGeneration() {
        when(insight.generate(any(), any(), any(), any(), any())).thenAnswer(call -> {
            mutateInNewTransaction(session -> session.updateStatus(MeasurementStatus.FAILED, null));
            return new MetricInsightGenerationService.MetricInsight(call.getArgument(4), call.getArgument(3));
        });

        assertThatThrownBy(() -> reports.saveMetricResult(userId, metricRequest(MetricType.ATHLETES_FOOT)))
                .isInstanceOf(MeasurementHandler.class);

        assertThat(reportRepository.findByMeasurementSessionId(sessionId)).isEmpty();
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.FAILED);
    }

    @Test
    void successfulBalanceAndPressureSummaryRecordPressureProgress() {
        var balance = request(new ReportRequestDTO.BalancePartDTO());
        ReflectionTestUtils.setField(balance, "balanceScore", 80f);
        reports.saveBalancePart(userId, balance);
        assertThat(sessions.findById(sessionId).orElseThrow().getPressureProgressAt()).isNotNull();
        mutate(session -> ReflectionTestUtils.setField(session, "pressureProgressAt", LocalDateTime.now().minusHours(1)));
        when(insight.generate(any(), any(), any(), any(), any())).thenAnswer(call ->
                new MetricInsightGenerationService.MetricInsight(call.getArgument(4), call.getArgument(3)));

        reports.saveMetricResult(userId, metricRequest(MetricType.PRESSURE_BALANCE));

        assertThat(sessions.findById(sessionId).orElseThrow().getPressureProgressAt())
                .isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void repeatedMetricSummariesDoNotDuplicateExistingPressureSensorReadings() {
        save(ImagePart.HEATMAP);
        when(insight.generate(any(), any(), any(), any(), any())).thenAnswer(call ->
                new MetricInsightGenerationService.MetricInsight(call.getArgument(4), call.getArgument(3)));

        reports.saveMetricResult(userId, metricRequest(MetricType.PRESSURE_BALANCE));
        reports.saveMetricResult(userId, metricRequest(MetricType.PRESSURE_BALANCE));

        assertThat(readings.findByMeasurementSessionIdOrderByFootSideAsc(sessionId)).hasSize(2);
    }

    private void mutateInNewTransaction(Consumer<MeasurementSession> change) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        transaction.executeWithoutResult(ignored -> change.accept(sessions.findByIdForUpdate(sessionId).orElseThrow()));
    }

    private ReportRequestDTO.SaveMetricResultDTO metricRequest(MetricType metricType) {
        var request = request(new ReportRequestDTO.SaveMetricResultDTO());
        ReflectionTestUtils.setField(request, "metricType", metricType);
        ReflectionTestUtils.setField(request, "score", 75f);
        ReflectionTestUtils.setField(request, "advice", List.of("Advice"));
        return request;
    }

    @Test
    void concurrentPartsCreateOneDailyAnalysisWithoutLosingOtherFields() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> saves = List.of(
                    workers.submit(() -> after(start, () -> save(ImagePart.PLANTAR))),
                    workers.submit(() -> after(start, () -> save(ImagePart.PRESSURE))),
                    workers.submit(() -> after(start, () -> save(ImagePart.HEATMAP))),
                    workers.submit(() -> after(start, () -> reports.saveMetricsPart(userId, metricsRequest())))
            );
            start.countDown();
            for (Future<?> save : saves) save.get(10, TimeUnit.SECONDS);
        } finally {
            stop(workers);
        }
        Long analysisCount = transactions.execute(ignored -> em.createQuery(
                        "select count(a) from DailyFootAnalysis a where a.measurementSession.id = :id", Long.class)
                .setParameter("id", sessionId).getSingleResult());
        assertThat(analysisCount).isOne();
        assertSaved(ImagePart.PLANTAR);
        assertSaved(ImagePart.PRESSURE);
        assertSaved(ImagePart.HEATMAP);
        assertThat(analyses.findByMeasurementSessionId(sessionId).orElseThrow().getMeasuredLeftFootSizeMm())
                .isEqualTo(250f);
    }

    @ParameterizedTest
    @EnumSource(ImagePart.class)
    void concurrentSamePartCallbacksUpdateOneRecord(ImagePart part) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = workers.submit(() -> after(start, () -> save(part)));
            Future<?> second = workers.submit(() -> after(start, () -> save(part)));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            stop(workers);
        }
        String entity = switch (part) {
            case HALLUX -> "HalluxValgusAnalysis";
            case TINEA -> "TinaPedisAnalysis";
            default -> "DailyFootAnalysis";
        };
        Long count = transactions.execute(ignored -> em.createQuery(
                        "select count(a) from " + entity + " a where a.measurementSession.id = :id", Long.class)
                .setParameter("id", sessionId).getSingleResult());
        assertThat(count).isOne();
        assertSaved(part);
    }

    @ParameterizedTest
    @EnumSource(ImagePart.class)
    void photoAttemptChangingDuringUploadRejectsAllImageWrites(ImagePart part) {
        duringFirstUpload(() -> mutate(session -> {
            session.startPhotoRecapture();
            session.updateStatus(MeasurementStatus.WAITING_FOR_PRESSURE, null);
        }));

        assertRejected(part, ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);
        assertNothingSaved();
    }

    @ParameterizedTest
    @EnumSource(value = ImagePart.class, names = {"PRESSURE", "HEATMAP"})
    void pressureAttemptChangingDuringUploadRejectsPressureWrites(ImagePart part) {
        duringFirstUpload(() -> mutate(session -> {
            session.startPressureRetry();
            session.updateStatus(MeasurementStatus.MEASURING_PRESSURE, null);
        }));

        assertRejected(part, ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);
        assertNothingSaved();
    }

    @ParameterizedTest
    @EnumSource(value = ImagePart.class, names = {"HALLUX", "TINEA", "PLANTAR"})
    void pressureRetryDuringUploadDoesNotInvalidatePhotoResults(ImagePart part) {
        duringFirstUpload(() -> mutate(session -> {
            session.startPressureRetry();
            session.requirePressureRetry("Adjust foot position");
        }));

        save(part);
        assertSaved(part);
        assertThat(sessions.findById(sessionId).orElseThrow().getPressureRetryCount()).isOne();
    }

    @ParameterizedTest
    @EnumSource(value = MeasurementStatus.class, names = {"FAILED", "WAITING_FOR_RECAPTURE", "READY_FOR_RECAPTURE"})
    void statusChangingDuringUploadRejectsStaleWrites(MeasurementStatus status) {
        duringFirstUpload(() -> mutate(session -> session.updateStatus(status, null)));

        assertRejected(ImagePart.HALLUX, status == MeasurementStatus.FAILED
                ? ErrorStatus.MEASUREMENT_ALREADY_FAILED : ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertNothingSaved();
    }

    @ParameterizedTest
    @EnumSource(value = ImagePart.class, names = {"PRESSURE", "HEATMAP"})
    void pressureRetryRequestedDuringUploadRejectsPressureWritesEvenBeforeCounterIncrements(ImagePart part) {
        duringFirstUpload(() -> mutate(session -> session.requirePressureRetry("Retry needed")));

        assertRejected(part, ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        assertNothingSaved();
    }

    @Test
    void deletedSessionDuringUploadCannotRecreateAnalysis() {
        duringFirstUpload(() -> transactions.executeWithoutResult(ignored -> sessions.deleteById(sessionId)));

        assertRejected(ImagePart.HALLUX, ErrorStatus.MEASUREMENT_NOT_FOUND);
        assertNothingSaved();
    }

    @Test
    void invalidOwnerOrFailedSessionDoesNotUpload() {
        var request = halluxRequest();
        assertThatThrownBy(() -> reports.saveHalluxValgusAnalysis(userId + 1, request, image(), image()))
                .isInstanceOf(MeasurementHandler.class);
        mutate(session -> session.updateStatus(MeasurementStatus.FAILED, null));
        assertRejected(ImagePart.HALLUX, ErrorStatus.MEASUREMENT_ALREADY_FAILED);
        verifyNoInteractions(images, completion);
    }

    @Test
    void failedUploadDoesNotChangeExistingAnalysis() {
        save(ImagePart.PLANTAR);
        var original = analyses.findByMeasurementSessionId(sessionId).orElseThrow();
        clearInvocations(completion);
        when(images.upload(anyString(), any())).thenThrow(new IllegalStateException("upload failed"));

        assertThatThrownBy(() -> save(ImagePart.PRESSURE)).hasMessage("upload failed");

        var reloaded = analyses.findByMeasurementSessionId(sessionId).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(original.getId());
        assertThat(reloaded.getLeftPlantarFootprintImageUrl()).isEqualTo(original.getLeftPlantarFootprintImageUrl());
        assertThat(reloaded.getLeftPressurePercent()).isNull();
        verify(completion, never()).refreshPressureAnalysisCompleted(any());
        verify(completion).resetPressureAnalysisForRetry(any());
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
    }

    @Test
    void databaseFailureRollsBackImagesSensorReplacementAndCompletionSocket() {
        save(ImagePart.HEATMAP);
        var originalReadings = readings.findByMeasurementSessionIdOrderByFootSideAsc(sessionId);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            MeasurementSession session = call.getArgument(0);
            session.updateStatus(MeasurementStatus.COMPLETED, null);
            em.flush();
            sockets.sendMeasurementCompleted(session, 1L);
            throw new IllegalStateException("database save failed");
        }).when(completion).refreshPressureAnalysisCompleted(any());
        when(images.upload(anyString(), any())).thenReturn(uploaded("replacement"));

        assertThatThrownBy(() -> save(ImagePart.HEATMAP)).hasMessage("database save failed");

        assertThat(readings.findByMeasurementSessionIdOrderByFootSideAsc(sessionId))
                .extracting(PressureSensorReading::getId)
                .containsExactlyElementsOf(originalReadings.stream().map(PressureSensorReading::getId).toList());
        assertThat(analyses.findByMeasurementSessionId(sessionId).orElseThrow().getLeftPressureHeatmapImageUrl())
                .isEqualTo(uploaded("pressure-heatmap-left").getImageUrl());
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        var messages = org.mockito.ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(broker, times(2)).convertAndSend(anyString(), messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
            assertThat(message.getShouldDisconnect()).isFalse();
            assertThat(message.getFailureReason()).isEqualTo(com.feetfit.server.domain.enums.MeasurementFailureReason.REPORT_SAVE_ERROR);
        });
    }

    @Test
    void balanceSaveFailureRollsBackBeforeCommittingRetryInAnotherTransaction() {
        save(ImagePart.PLANTAR);
        clearInvocations(completion, broker);
        doThrow(new DataAccessResourceFailureException("database write failed")).when(analyses).flush();
        var request = request(new ReportRequestDTO.BalancePartDTO());
        ReflectionTestUtils.setField(request, "balanceScore", 70f);
        ReflectionTestUtils.setField(request, "balanceComment", "Balance comment");

        assertThatThrownBy(() -> reports.saveBalancePart(userId, request)).hasMessage("database write failed");

        assertThat(analyses.findByMeasurementSessionId(sessionId).orElseThrow().getBalanceScore()).isNull();
        assertThat(analyses.findByMeasurementSessionId(sessionId).orElseThrow().getPlantarFootprintAnalysisText())
                .isEqualTo("Plantar analysis");
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        verify(completion).resetPressureAnalysisForRetry(any());
    }

    @Test
    void pressureSummaryFailureRollsBackNewReportAndRequestsRetry() {
        when(insight.generate(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("summary save failed"));
        var request = request(new ReportRequestDTO.SaveMetricResultDTO());
        ReflectionTestUtils.setField(request, "metricType", com.feetfit.server.domain.enums.MetricType.PRESSURE_BALANCE);
        ReflectionTestUtils.setField(request, "score", 75f);
        ReflectionTestUtils.setField(request, "advice", List.of("Advice"));

        assertThatThrownBy(() -> reports.saveMetricResult(userId, request)).hasMessage("summary save failed");

        assertThat(reportRepository.findByMeasurementSessionId(sessionId)).isEmpty();
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY);
        verify(completion).resetPressureAnalysisForRetry(any());
    }

    @Test
    void successfulSaveInAnIntentionallyRolledBackTransactionDoesNotRequestRetry() {
        var request = request(new ReportRequestDTO.BalancePartDTO());
        ReflectionTestUtils.setField(request, "balanceScore", 80f);
        transactions.executeWithoutResult(status -> {
            reports.saveBalancePart(userId, request);
            status.setRollbackOnly();
        });

        assertThat(analyses.findByMeasurementSessionId(sessionId)).isEmpty();
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.READY_FOR_PRESSURE);
        verifyNoInteractions(broker, completion);
    }

    @Test
    void invalidUploadRequestDoesNotChangeMeasurementState() {
        when(images.upload(anyString(), any())).thenThrow(new com.feetfit.server.apiPayload.exception.GeneralException(
                ErrorStatus._BAD_REQUEST));

        assertThatThrownBy(() -> save(ImagePart.HEATMAP))
                .isInstanceOf(com.feetfit.server.apiPayload.exception.GeneralException.class);

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.READY_FOR_PRESSURE);
        verifyNoInteractions(broker, completion);
    }

    @Test
    void openInViewCachedSessionIsReloadedBeforeFinalValidation() throws Exception {
        EntityManager openInView = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(openInView));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            duringFirstUpload(() -> {
                try {
                    worker.submit(() -> mutate(session -> {
                        session.startPhotoRecapture();
                        session.updateStatus(MeasurementStatus.WAITING_FOR_PRESSURE, null);
                    })).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertRejected(ImagePart.HALLUX, ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            openInView.close();
            stop(worker);
        }
        assertNothingSaved();
    }

    @Test
    void openInViewSessionCanStillBeCompletedInTheSaveTransaction() {
        EntityManager openInView = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(openInView));
        try {
            doAnswer(call -> {
                MeasurementSession session = call.getArgument(0);
                session.updateStatus(MeasurementStatus.COMPLETED, null);
                return null;
            }).when(completion).refreshPhotoAnalysisCompleted(any());
            save(ImagePart.HALLUX);
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            openInView.close();
        }
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
    }

    private void assertRejected(ImagePart part, ErrorStatus expected) {
        assertThatThrownBy(() -> save(part)).isInstanceOfSatisfying(MeasurementHandler.class,
                error -> assertThat(error.getCode()).isEqualTo(expected));
    }

    private void assertNothingSaved() {
        assertThat(hallux.findByMeasurementSessionId(sessionId)).isEmpty();
        assertThat(tinea.findByMeasurementSessionId(sessionId)).isEmpty();
        assertThat(analyses.findByMeasurementSessionId(sessionId)).isEmpty();
        assertThat(readings.findByMeasurementSessionIdOrderByFootSideAsc(sessionId)).isEmpty();
        verifyNoInteractions(completion);
    }

    private void assertSaved(ImagePart part) {
        switch (part) {
            case HALLUX -> assertThat(hallux.findByMeasurementSessionId(sessionId).orElseThrow().getLeftImageUrl())
                    .isEqualTo(uploaded("hallux-valgus-left").getImageUrl());
            case TINEA -> assertThat(tinea.findByMeasurementSessionId(sessionId).orElseThrow().getSoleOriginalFootImageUrl())
                    .isEqualTo(uploaded("tina-pedis-sole-original").getImageUrl());
            case PLANTAR -> assertThat(analyses.findByMeasurementSessionId(sessionId).orElseThrow().getPlantarFootprintAnalysisText())
                    .isEqualTo("Plantar analysis");
            case PRESSURE -> assertThat(analyses.findByMeasurementSessionId(sessionId).orElseThrow().getLeftPressurePercent())
                    .isEqualTo(60f);
            case HEATMAP -> {
                assertThat(analyses.findByMeasurementSessionId(sessionId).orElseThrow().getLeftPressureHeatmapImageUrl())
                        .isEqualTo(uploaded("pressure-heatmap-left").getImageUrl());
                assertThat(readings.findByMeasurementSessionIdOrderByFootSideAsc(sessionId)).hasSize(2)
                        .allSatisfy(reading -> assertThat(reading.getSensorValues()).hasSize(12));
            }
        }
    }

    private void save(ImagePart part) {
        switch (part) {
            case HALLUX -> reports.saveHalluxValgusAnalysis(userId, halluxRequest(), image(), image());
            case TINEA -> {
                var request = request(new ReportRequestDTO.SaveTinaPedisAnalysisDTO());
                ReflectionTestUtils.setField(request, "fungalSuspicionSafetyScore", 90);
                ReflectionTestUtils.setField(request, "skinReactionSafetyScore", 80);
                ReflectionTestUtils.setField(request, "fungalSuspicionSafetyDescription", "Fungal description");
                ReflectionTestUtils.setField(request, "skinReactionSafetyDescription", "Skin description");
                ReflectionTestUtils.setField(request, "totalScoreDescription", "Total description");
                reports.saveTinaPedisAnalysis(userId, request, image(), image(), image(), image());
            }
            case PLANTAR -> {
                var request = request(new ReportRequestDTO.PlantarFootprintImageDTO());
                ReflectionTestUtils.setField(request, "plantarFootprintAnalysisText", "Plantar analysis");
                reports.savePlantarFootprintImage(userId, request, image(), image());
            }
            case PRESSURE -> {
                var request = request(new ReportRequestDTO.PressurePartDTO());
                ReflectionTestUtils.setField(request, "leftPressurePercent", 60f);
                ReflectionTestUtils.setField(request, "rightPressurePercent", 40f);
                reports.savePressurePart(userId, request, image(), image());
            }
            case HEATMAP -> {
                var request = request(new ReportRequestDTO.PressureHeatmapImageDTO());
                ReflectionTestUtils.setField(request, "leftPressureValues", Collections.nCopies(12, 100f));
                ReflectionTestUtils.setField(request, "rightPressureValues", Collections.nCopies(12, 200f));
                reports.savePressureHeatmapImage(userId, request, image(), image());
            }
        }
    }

    private ReportRequestDTO.SaveHalluxValgusDTO halluxRequest() {
        var request = request(new ReportRequestDTO.SaveHalluxValgusDTO());
        ReflectionTestUtils.setField(request, "leftToeAngleDegree", 10f);
        ReflectionTestUtils.setField(request, "rightToeAngleDegree", 12f);
        ReflectionTestUtils.setField(request, "scoreAnalysisText", "Hallux description");
        return request;
    }

    private ReportRequestDTO.MetricsPartDTO metricsRequest() {
        var request = request(new ReportRequestDTO.MetricsPartDTO());
        ReflectionTestUtils.setField(request, "measuredLeftFootSizeMm", 250f);
        ReflectionTestUtils.setField(request, "measuredRightFootSizeMm", 251f);
        ReflectionTestUtils.setField(request, "leftFootWidthMm", 100f);
        ReflectionTestUtils.setField(request, "rightFootWidthMm", 101f);
        return request;
    }

    private <T> T request(T request) {
        ReflectionTestUtils.setField(request, "measurementSessionId", sessionId);
        return request;
    }

    private void mutate(Consumer<MeasurementSession> update) {
        transactions.executeWithoutResult(ignored -> update.accept(sessions.findByIdForUpdate(sessionId).orElseThrow()));
    }

    private void duringFirstUpload(Runnable action) {
        AtomicBoolean first = new AtomicBoolean(true);
        when(images.upload(anyString(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (first.compareAndSet(true, false)) action.run();
            return uploaded(call.getArgument(0));
        });
    }

    private void blockUploads(CountDownLatch uploading, CountDownLatch release) {
        when(images.upload(anyString(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            uploading.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return uploaded(call.getArgument(0));
        });
    }

    private static void after(CountDownLatch start, Runnable action) {
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void stop(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow();
        assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private static ImageResponseDTO.UploadImageResultDTO uploaded(String folder) {
        return ImageResponseDTO.UploadImageResultDTO.builder().imageUrl("https://example.com/" + folder + ".png").build();
    }

    private static MultipartFile image() {
        return new MockMultipartFile("image", "foot.png", "image/png", new byte[]{1, 2, 3});
    }

    private enum ImagePart { HALLUX, TINEA, PLANTAR, PRESSURE, HEATMAP }
}
