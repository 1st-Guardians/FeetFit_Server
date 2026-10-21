package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.repository.*;
import com.feetfit.server.service.ImageService.ImageUploadService;
import com.feetfit.server.service.ReportService.MetricInsightGenerationService;
import com.feetfit.server.service.ReportService.ReportCommandService;
import com.feetfit.server.service.ReportService.ReportCommandServiceImpl;
import com.feetfit.server.web.controller.MeasurementController;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MeasurementCommandServiceImpl.class, MeasurementWriteTransactions.class,
        ReportCommandServiceImpl.class, MeasurementCompletionService.class})
class MeasurementPressureStartJpaIntegrationTest {
    @Autowired MeasurementCommandService commands;
    @Autowired ReportCommandService reports;
    @Autowired MeasurementSessionRepository sessions;
    @Autowired MeasurementAnalysisStatusRepository flags;
    @Autowired TinaPedisAnalysisRepository tineaAnalyses;
    @Autowired DailyFootAnalysisRepository dailyAnalyses;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager manager;
    @MockBean MeasurementHardwareClient hardware;
    @MockBean MeasurementSocketService sockets;
    @MockBean ImageUploadService uploads;
    @MockBean MetricInsightGenerationService insights;
    @MockBean MeasurementCareTipsGenerationService careTips;

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private ExecutorService workers;
    private TransactionTemplate transactions;
    private MockMvc mvc;
    private Long userId;
    private Long sessionId;
    private String blockedFolder;
    private boolean blockInsight;

    @BeforeEach
    void setup() {
        workers = Executors.newFixedThreadPool(3);
        transactions = new TransactionTemplate(manager);
        transactions.executeWithoutResult(ignored -> {
            User user = User.builder().nickname("pressure-start").socialType(SocialType.KAKAO)
                    .socialId(UUID.randomUUID().toString()).build();
            entityManager.persist(user);
            Device device = Device.builder().deviceName("pressure-" + UUID.randomUUID()).build();
            entityManager.persist(device);
            MeasurementSession session = MeasurementSession.builder().user(user).device(device)
                    .measuredAt(LocalDateTime.now()).status(MeasurementStatus.READY_FOR_PRESSURE).build();
            entityManager.persist(session);
            entityManager.persist(MeasurementAnalysisStatus.builder().measurementSession(session).build());
            userId = user.getId();
            sessionId = session.getId();
        });
        FindLoginUser login = mock(FindLoginUser.class);
        when(login.getCurrentUserId()).thenReturn(userId);
        mvc = MockMvcBuilders.standaloneSetup(new MeasurementController(
                commands, mock(MeasurementQueryService.class), sockets, login)).build();
        lenient().when(uploads.upload(anyString(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            String folder = invocation.getArgument(0);
            if (folder.equals(blockedFolder)) waitForRelease();
            return ImageResponseDTO.UploadImageResultDTO.builder()
                    .imageUrl("https://example.com/" + folder + ".png").build();
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

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void firstMeasuringPatchCommitsWhilePhotoUploadIsStillRunning(int attempt) throws Exception {
        if (attempt == 1) {
            transactions.executeWithoutResult(ignored -> {
                MeasurementSession session = sessions.findByIdForUpdate(sessionId).orElseThrow();
                session.startPressureRetry();
                session.updateStatus(MeasurementStatus.READY_FOR_PRESSURE_RETRY, null);
            });
        }
        blockedFolder = "tina-pedis-map";
        var photo = workers.submit(() -> reports.saveTinaPedisAnalysis(userId, tinea(), image(), image(), image(), image()));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            workers.submit(() -> measuringPatch(attempt)).get(2, TimeUnit.SECONDS);
            assertThat(photo.isDone()).isFalse();
            assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
            verify(sockets).sendMeasurementStatusChanged(any());
            verifyNoInteractions(hardware, careTips);
        } finally {
            release.countDown();
        }
        photo.get(10, TimeUnit.SECONDS);
        TinaPedisAnalysis saved = tineaAnalyses.findByMeasurementSessionId(sessionId).orElseThrow();
        assertThat(saved.getOriginalFootImageUrl()).contains("tina-pedis-original");
        assertThat(saved.getSoleSuspiciousAreaMapImageUrl()).contains("tina-pedis-sole-map");
        assertThat(saved.getFungalSuspicionSafetyScore()).isEqualTo(90);
        assertThat(sessions.findById(sessionId).orElseThrow().getPressureRetryCount()).isEqualTo(attempt);
    }

    @Test
    void simultaneousFirstMeasuringCallbacksCommitAndPublishOnlyOnce() throws Exception {
        CyclicBarrier start = new CyclicBarrier(3);
        var requests = new java.util.ArrayList<Future<MvcResult>>();
        for (int i = 0; i < 3; i++) {
            requests.add(workers.submit(() -> {
                start.await();
                return measuringPatch(0);
            }));
        }
        for (var request : requests) request.get(2, TimeUnit.SECONDS);

        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
        verify(sockets).sendMeasurementStatusChanged(any());
        verifyNoInteractions(hardware, uploads, insights, careTips);
    }

    @Test
    void firstMeasuringPatchDoesNotWaitForTheAnalysisFlagsLock() throws Exception {
        var analysisWriter = workers.submit(() -> transactions.executeWithoutResult(ignored -> {
            flags.findByMeasurementSessionIdForUpdate(sessionId).orElseThrow();
            waitForRelease();
        }));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            workers.submit(() -> measuringPatch(0)).get(2, TimeUnit.SECONDS);
            assertThat(analysisWriter.isDone()).isFalse();
            assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
        } finally {
            release.countDown();
        }
        analysisWriter.get(10, TimeUnit.SECONDS);
    }

    @Test
    void firstMeasuringPatchDoesNotWaitForPhotoMetricTextGeneration() throws Exception {
        blockInsight = true;
        var request = request(new ReportRequestDTO.SaveMetricResultDTO());
        set(request, "metricType", MetricType.ATHLETES_FOOT);
        set(request, "score", 80f);
        set(request, "advice", List.of("Photo result"));
        var metric = workers.submit(() -> reports.saveMetricResult(userId, request));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            workers.submit(() -> measuringPatch(0)).get(2, TimeUnit.SECONDS);
            assertThat(metric.isDone()).isFalse();
        } finally {
            release.countDown();
        }
        assertThat(metric.get(10, TimeUnit.SECONDS).getMeasurementSessionId()).isEqualTo(sessionId);
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
    }

    @Test
    void firstMeasuringPatchWaitsForAShortPhotoDatabaseWriteWithoutLosingItsResult() throws Exception {
        var photoWriter = workers.submit(() -> transactions.executeWithoutResult(ignored -> {
            MeasurementSession session = sessions.findByIdForUpdate(sessionId).orElseThrow();
            DailyFootAnalysis analysis = DailyFootAnalysis.builder().measurementSession(session).build();
            analysis.updateMetrics(245f, 246f, 90f, 91f);
            entityManager.persist(analysis);
            entityManager.flush();
            waitForRelease();
        }));
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        var pressure = workers.submit(() -> measuringPatch(0));
        try {
            assertThatThrownBy(() -> pressure.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        } finally {
            release.countDown();
        }
        photoWriter.get(10, TimeUnit.SECONDS);
        pressure.get(2, TimeUnit.SECONDS);
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
        assertThat(dailyAnalyses.findByMeasurementSessionId(sessionId).orElseThrow().getMeasuredLeftFootSizeMm()).isEqualTo(245f);
    }

    @Test
    void resultCallbacksStillUpdatePhotoAndPressureFlagsAfterStartingTheSensors() throws Exception {
        measuringPatch(0);
        assertThat(flags.findByMeasurementSessionId(sessionId).orElseThrow().isPhotoCaptureCompleted()).isFalse();
        reports.saveTinaPedisAnalysis(userId, tinea(), image(), image(), image(), image());
        var hallux = request(new ReportRequestDTO.SaveHalluxValgusDTO());
        set(hallux, "leftToeAngleDegree", 10f);
        set(hallux, "rightToeAngleDegree", 12f);
        set(hallux, "scoreAnalysisText", "Photo result");
        reports.saveHalluxValgusAnalysis(userId, hallux, image(), image());
        var metrics = request(new ReportRequestDTO.MetricsPartDTO());
        set(metrics, "measuredLeftFootSizeMm", 245f);
        set(metrics, "measuredRightFootSizeMm", 246f);
        set(metrics, "leftFootWidthMm", 90f);
        set(metrics, "rightFootWidthMm", 91f);
        reports.saveMetricsPart(userId, metrics);
        var plantar = request(new ReportRequestDTO.PlantarFootprintImageDTO());
        set(plantar, "plantarFootprintAnalysisText", "Photo result");
        reports.savePlantarFootprintImage(userId, plantar, image(), image());
        var heatmap = request(new ReportRequestDTO.PressureHeatmapImageDTO());
        set(heatmap, "leftPressureValues", Collections.nCopies(12, 100f));
        set(heatmap, "rightPressureValues", Collections.nCopies(12, 120f));
        reports.savePressureHeatmapImage(userId, heatmap, image(), image());
        var pressure = request(new ReportRequestDTO.PressurePartDTO());
        set(pressure, "leftPressurePercent", 52f);
        set(pressure, "rightPressurePercent", 48f);
        reports.savePressurePart(userId, pressure, image(), image());

        MeasurementAnalysisStatus saved = flags.findByMeasurementSessionId(sessionId).orElseThrow();
        assertThat(saved.isPhotoCaptureCompleted()).isTrue();
        assertThat(saved.isPhotoAnalysisCompleted()).isTrue();
        assertThat(saved.isPressureCaptureCompleted()).isTrue();
        assertThat(saved.isPressureAnalysisCompleted()).isTrue();
    }

    private MvcResult measuringPatch(int attempt) throws Exception {
        return mvc.perform(patch("/api/measurement-sessions/{id}/status", sessionId)
                        .header("Authorization", "Bearer test")
                        .param("status", "MEASURING_PRESSURE")
                        .param("pressureMeasurementAttempt", Integer.toString(attempt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("MEASURING_PRESSURE"))
                .andExpect(jsonPath("$.result.pressureMeasurementAttempt").value(attempt))
                .andReturn();
    }

    private ReportRequestDTO.SaveTinaPedisAnalysisDTO tinea() {
        var request = request(new ReportRequestDTO.SaveTinaPedisAnalysisDTO());
        set(request, "fungalSuspicionSafetyScore", 90);
        set(request, "skinReactionSafetyScore", 85);
        set(request, "fungalSuspicionSafetyDescription", "Photo result");
        set(request, "skinReactionSafetyDescription", "Photo result");
        set(request, "totalScoreDescription", "Photo result");
        return request;
    }

    private <T> T request(T request) {
        set(request, "measurementSessionId", sessionId);
        return request;
    }

    private void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }

    private MultipartFile image() {
        return new MockMultipartFile("image", "foot.png", "image/png", new byte[]{1, 2});
    }

    private void waitForRelease() {
        entered.countDown();
        try {
            assertThat(release.await(8, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating concurrent work", failure);
        }
    }
}
