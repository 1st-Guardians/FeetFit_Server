package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.*;
import com.feetfit.server.repository.*;
import com.feetfit.server.service.ImageService.ImageUploadService;
import com.feetfit.server.service.ReportService.MetricInsightGenerationService;
import com.feetfit.server.service.ReportService.ReportCommandService;
import com.feetfit.server.service.ReportService.ReportCommandServiceImpl;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MeasurementCommandServiceImpl.class,
        MeasurementWriteTransactions.class, ReportCommandServiceImpl.class})
class PressureStatusConcurrencyJpaIntegrationTest {
    @Autowired ReportCommandService reports;
    @Autowired MeasurementCommandService commands;
    @Autowired MeasurementSessionRepository sessions;
    @Autowired DailyFootAnalysisRepository analyses;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager manager;
    @MockBean ImageUploadService uploads;
    @MockBean MetricInsightGenerationService insights;
    @MockBean MeasurementHardwareClient hardware;
    @MockBean MeasurementCompletionService completion;
    @MockBean MeasurementSocketService sockets;

    private TransactionTemplate transactions;
    private ExecutorService workers;
    private Long userId;
    private Long sessionId;
    private String blockedFolder;
    private boolean blockInsight;
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setup() {
        workers = Executors.newFixedThreadPool(3);
        transactions = new TransactionTemplate(manager);
        transactions.executeWithoutResult(ignored -> {
            User user = User.builder().nickname("pressure-concurrency").socialType(SocialType.KAKAO)
                    .socialId(UUID.randomUUID().toString()).build();
            entityManager.persist(user);
            Device device = Device.builder().deviceName("pressure-" + UUID.randomUUID()).build();
            entityManager.persist(device);
            MeasurementSession session = MeasurementSession.builder().user(user).device(device)
                    .measuredAt(LocalDateTime.now()).status(MeasurementStatus.MEASURING_PRESSURE).build();
            entityManager.persist(session);
            userId = user.getId();
            sessionId = session.getId();
        });
        lenient().when(uploads.upload(anyString(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            String folder = invocation.getArgument(0);
            if (folder.equals(blockedFolder)) waitForRelease();
            return ImageResponseDTO.UploadImageResultDTO.builder().imageUrl("https://example.com/" + folder + ".png").build();
        });
        lenient().when(insights.generate(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            if (blockInsight) waitForRelease();
            return new MetricInsightGenerationService.MetricInsight(invocation.getArgument(4), invocation.getArgument(3));
        });
    }

    @AfterEach
    void stopWorkers() throws InterruptedException {
        release.countDown();
        workers.shutdownNow();
        assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void slowPhotoUploadDoesNotBlockExistingPressureStatusCallbacks() throws Exception {
        blockedFolder = "tina-pedis-map";
        var photo = workers.submit(() -> reports.saveTinaPedisAnalysis(userId, tinea(), image(), image(), image(), image()));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            var validation = workers.submit(() -> update(MeasurementStatus.MEASURING_PRESSURE, 0));
            assertThat(validation.get(2, TimeUnit.SECONDS).getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
            var state = workers.submit(() -> update(MeasurementStatus.ANALYZING, 0));
            assertThat(state.get(2, TimeUnit.SECONDS).getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        } finally {
            release.countDown();
        }
        assertThat(photo.get(10, TimeUnit.SECONDS).getMeasurementSessionId()).isEqualTo(sessionId);
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
    }

    @Test
    void existingSameStatePatchDoesNotWaitForAnActiveWriteLock() throws Exception {
        var writer = workers.submit(() -> transactions.executeWithoutResult(ignored -> {
            MeasurementSession session = sessions.findByIdForUpdate(sessionId).orElseThrow();
            session.updateStatus(MeasurementStatus.ANALYZING, null);
            waitForRelease();
        }));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            var result = workers.submit(() -> update(MeasurementStatus.MEASURING_PRESSURE, 0))
                    .get(2, TimeUnit.SECONDS);
            assertThat(result.getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
        } finally {
            release.countDown();
        }
        writer.get(10, TimeUnit.SECONDS);
        assertThat(update(MeasurementStatus.ANALYZING, 0).getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
    }

    @Test
    void simultaneousPhotoPressureAndEnvironmentWritesDoNotLoseEachOthersFields() throws Exception {
        CyclicBarrier start = new CyclicBarrier(3);
        var photo = workers.submit(() -> { start.await(); return reports.savePlantarFootprintImage(userId, plantar(), image(), image()); });
        var pressure = workers.submit(() -> { start.await(); return reports.savePressurePart(userId, pressure(), image(), image()); });
        var environment = workers.submit(() -> { start.await(); return reports.saveEnvironmentPart(userId, environment()); });
        photo.get(10, TimeUnit.SECONDS);
        pressure.get(10, TimeUnit.SECONDS);
        environment.get(10, TimeUnit.SECONDS);

        DailyFootAnalysis analysis = analyses.findByMeasurementSessionId(sessionId).orElseThrow();
        assertThat(analysis.getLeftPlantarFootprintImageUrl()).contains("plantar-footprint-left");
        assertThat(analysis.getPlantarFootprintAnalysisText()).isEqualTo("Photo result");
        assertThat(analysis.getLeftPressurePercent()).isEqualTo(52f);
        assertThat(analysis.getRightPressureImageUrl()).contains("pressure-right");
        assertThat(analysis.getAvgTemperatureCelsius()).isEqualTo(26f);
        assertThat(analysis.getAvgHumidityPercent()).isEqualTo(50f);
        Long count = transactions.execute(ignored -> entityManager.createQuery(
                "select count(a) from DailyFootAnalysis a where a.measurementSession.id = :id", Long.class)
                .setParameter("id", sessionId).getSingleResult());
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void halluxImageUploadsAlsoRunWithoutAnActiveTransaction() {
        var request = new ReportRequestDTO.SaveHalluxValgusDTO();
        set(request, "measurementSessionId", sessionId);
        set(request, "leftToeAngleDegree", 10f);
        set(request, "rightToeAngleDegree", 12f);
        set(request, "scoreAnalysisText", "Photo result");

        assertThat(reports.saveHalluxValgusAnalysis(userId, request, image(), image()).getMeasurementSessionId())
                .isEqualTo(sessionId);
    }

    @Test
    void simultaneousHeatmapAndDistributionPreserveBothImagesAndAllSensorValues() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        var request = new ReportRequestDTO.PressureHeatmapImageDTO();
        set(request, "measurementSessionId", sessionId);
        set(request, "leftPressureValues", java.util.Collections.nCopies(12, 100f));
        set(request, "rightPressureValues", java.util.Collections.nCopies(12, 120f));
        var heatmap = workers.submit(() -> {
            start.await();
            return reports.savePressureHeatmapImage(userId, request, image(), image());
        });
        var distribution = workers.submit(() -> {
            start.await();
            return reports.savePressurePart(userId, pressure(), image(), image());
        });

        assertThat(heatmap.get(10, TimeUnit.SECONDS).getPressureSensorValueCount()).isEqualTo(24);
        distribution.get(10, TimeUnit.SECONDS);
        DailyFootAnalysis analysis = analyses.findByMeasurementSessionId(sessionId).orElseThrow();
        assertThat(analysis.getLeftPressureHeatmapImageUrl()).contains("pressure-heatmap-left");
        assertThat(analysis.getRightPressureHeatmapImageUrl()).contains("pressure-heatmap-right");
        assertThat(analysis.getLeftPressureImageUrl()).contains("pressure-left");
        assertThat(analysis.getRightPressurePercent()).isEqualTo(48f);
    }

    @Test
    void slowTextGenerationDoesNotBlockPressureStatus() throws Exception {
        blockInsight = true;
        var metric = workers.submit(() -> reports.saveMetricResult(userId, metric(MetricType.ATHLETES_FOOT)));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            var state = workers.submit(() -> update(MeasurementStatus.ANALYZING, 0));
            assertThat(state.get(2, TimeUnit.SECONDS).getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        } finally {
            release.countDown();
        }
        assertThat(metric.get(10, TimeUnit.SECONDS).getMeasurementSessionId()).isEqualTo(sessionId);
    }

    @Test
    void retryDuringUploadRejectsTheOldImageWrite() throws Exception {
        blockedFolder = "pressure-left";
        var oldWrite = workers.submit(() -> reports.savePressurePart(userId, pressure(), image(), image()));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            startRetry();
        } finally {
            release.countDown();
        }
        assertThatThrownBy(() -> oldWrite.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(MeasurementHandler.class);
        assertThat(analyses.findByMeasurementSessionId(sessionId)).isEmpty();
        assertThat(update(MeasurementStatus.MEASURING_PRESSURE, 1).getPressureMeasurementAttempt()).isEqualTo(1);
    }

    @Test
    void retryDuringTextGenerationRejectsTheOldSummary() throws Exception {
        blockInsight = true;
        var oldWrite = workers.submit(() -> reports.saveMetricResult(userId, metric(MetricType.PRESSURE_BALANCE)));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            startRetry();
        } finally {
            release.countDown();
        }
        assertThatThrownBy(() -> oldWrite.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(MeasurementHandler.class);
        Long count = transactions.execute(ignored -> entityManager.createQuery(
                "select count(m) from MetricAnalysisResult m where m.report.measurementSession.id = :id", Long.class)
                .setParameter("id", sessionId).getSingleResult());
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void existingReportPayloadWorksOnPressureRetryWithoutNewFieldsOrHeaders() {
        startRetry();
        var request = new ReportRequestDTO.BalancePartDTO();
        set(request, "measurementSessionId", sessionId);
        set(request, "balanceScore", 80f);
        set(request, "balanceComment", "Pressure result");
        assertThat(reports.saveBalancePart(userId, request).getBalanceScore()).isEqualTo(80f);
        assertThat(sessions.findById(sessionId).orElseThrow().getPressureRetryCount()).isEqualTo(1);
    }

    @Test
    void transientStatusWriteConflictsRollbackBeforeRetryAndPublishOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() < 3) throw new CannotAcquireLockException("transient conflict");
            return null;
        }).when(completion).refreshCaptureCompletedByStatus(any(), eq(MeasurementStatus.ANALYZING));

        assertThat(update(MeasurementStatus.ANALYZING, 0).getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
        assertThat(calls).hasValue(3);
        verify(sockets).sendMeasurementStatusChanged(any());
        verifyNoInteractions(hardware);
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.ANALYZING);
    }

    @Test
    void exhaustedStatusWriteConflictsLeaveTheOriginalStatusAndDoNotPublishSuccess() {
        doThrow(new CannotAcquireLockException("transient conflict"))
                .when(completion).refreshCaptureCompletedByStatus(any(), eq(MeasurementStatus.ANALYZING));

        assertThatThrownBy(() -> update(MeasurementStatus.ANALYZING, 0)).isInstanceOf(MeasurementHandler.class)
                .satisfies(error -> assertThat(((MeasurementHandler) error).getCode()).isEqualTo(ErrorStatus.MEASUREMENT_WRITE_UNAVAILABLE));
        verify(completion, times(3)).refreshCaptureCompletedByStatus(any(), eq(MeasurementStatus.ANALYZING));
        verifyNoInteractions(sockets, hardware);
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
    }

    private void startRetry() {
        update(MeasurementStatus.WAITING_FOR_PRESSURE_RETRY, 0);
        update(MeasurementStatus.READY_FOR_PRESSURE_RETRY, 0);
        update(MeasurementStatus.MEASURING_PRESSURE, 1);
    }

    private com.feetfit.server.web.dto.measurement.MeasurementResponseDTO.UpdateMeasurementStatusResultDTO update(
            MeasurementStatus status, int attempt) {
        return commands.updateMeasurementStatus(userId, sessionId,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(status, null, null, null, null, attempt), "Bearer test");
    }

    private void waitForRelease() {
        entered.countDown();
        try {
            assertThat(release.await(8, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating an external call", failure);
        }
    }

    private MultipartFile image() {
        return new MockMultipartFile("image", "foot.png", "image/png", new byte[]{1, 2});
    }

    private ReportRequestDTO.SaveTinaPedisAnalysisDTO tinea() {
        var request = new ReportRequestDTO.SaveTinaPedisAnalysisDTO();
        set(request, "measurementSessionId", sessionId);
        set(request, "fungalSuspicionSafetyScore", 90);
        set(request, "skinReactionSafetyScore", 85);
        set(request, "fungalSuspicionSafetyDescription", "Photo result");
        set(request, "skinReactionSafetyDescription", "Photo result");
        set(request, "totalScoreDescription", "Photo result");
        return request;
    }

    private ReportRequestDTO.PlantarFootprintImageDTO plantar() {
        var request = new ReportRequestDTO.PlantarFootprintImageDTO();
        set(request, "measurementSessionId", sessionId);
        set(request, "plantarFootprintAnalysisText", "Photo result");
        return request;
    }

    private ReportRequestDTO.PressurePartDTO pressure() {
        var request = new ReportRequestDTO.PressurePartDTO();
        set(request, "measurementSessionId", sessionId);
        set(request, "leftPressurePercent", 52f);
        set(request, "rightPressurePercent", 48f);
        return request;
    }

    private ReportRequestDTO.EnvironmentPartDTO environment() {
        var request = new ReportRequestDTO.EnvironmentPartDTO();
        set(request, "measurementSessionId", sessionId);
        set(request, "beforeTemperatureCelsius", 25f);
        set(request, "afterTemperatureCelsius", 27f);
        set(request, "beforeHumidityPercent", 45f);
        set(request, "afterHumidityPercent", 55f);
        return request;
    }

    private ReportRequestDTO.SaveMetricResultDTO metric(MetricType type) {
        var request = new ReportRequestDTO.SaveMetricResultDTO();
        set(request, "measurementSessionId", sessionId);
        set(request, "metricType", type);
        set(request, "score", 80f);
        set(request, "advice", List.of("Metric result"));
        return request;
    }

    private void set(Object target, String name, Object value) {
        ReflectionTestUtils.setField(target, name, value);
    }
}
