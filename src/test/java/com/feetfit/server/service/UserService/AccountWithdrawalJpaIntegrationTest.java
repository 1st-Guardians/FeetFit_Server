package com.feetfit.server.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.config.MeasurementWebSocketSessions;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.*;
import com.feetfit.server.repository.AccountCleanupTaskRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.service.ImageService.*;
import com.feetfit.server.service.MeasurementService.*;
import com.feetfit.server.service.ReportService.MetricInsightGenerationService;
import com.feetfit.server.service.ReportService.ReportCommandService;
import com.feetfit.server.service.ReportService.ReportCommandServiceImpl;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "file.upload.bucket-name=test-bucket",
        "file.upload.public-url-prefix=https://test-bucket.s3.us-west-2.amazonaws.com"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AccountWithdrawalService.class, AccountCleanupStore.class, AccountCleanupCipher.class,
        ObjectMapper.class, S3ObjectDeletionClient.class, ImageReferenceService.class,
        MeasurementCommandServiceImpl.class, MeasurementSocketService.class, MeasurementWebSocketSessions.class,
        ReportCommandServiceImpl.class, MeasurementPressureRecoveryService.class})
class AccountWithdrawalJpaIntegrationTest {
    private static final String PREFIX = "https://test-bucket.s3.us-west-2.amazonaws.com/";
    @Autowired AccountWithdrawalService withdrawal;
    @Autowired AccountCleanupStore cleanup;
    @Autowired AccountCleanupTaskRepository tasks;
    @Autowired UserRepository users;
    @Autowired MeasurementSessionRepository sessions;
    @Autowired MeasurementWebSocketSessions sockets;
    @Autowired ImageReferenceService references;
    @Autowired ObjectProvider<AccountCleanupStore> cleanupProvider;
    @Autowired ObjectProvider<S3ObjectDeletionClient> deletionProvider;
    @Autowired ReportCommandService reports;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean S3ObjectDeletionClient images;
    @MockBean ImageUploadService uploads;
    @MockBean SocialUnlinkClient social;
    @MockBean MeasurementHardwareClient hardware;
    @MockBean MeasurementCompletionService completion;
    @MockBean SimpMessagingTemplate broker;
    @MockBean MetricInsightGenerationService insight;
    private TransactionTemplate tx;
    private Long userId;
    private Long sessionId;
    private Long deviceId;
    private String subject;
    private String privateKey;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(transactionManager);
        tasks.deleteAll();
        subject = UUID.randomUUID().toString();
        privateKey = "hallux-valgus-left/" + UUID.randomUUID() + ".jpg";
        when(social.prepare(any(), anyString(), any())).thenReturn(new SocialUnlinkClient.Prepared(null, null));
        doNothing().when(images).delete(anyString(), anyString());
        tx.executeWithoutResult(ignored -> {
            Device device = persist(Device.builder().deviceName(subject).build());
            device.markConnected();
            User user = persist(User.builder().nickname("withdrawal").socialType(SocialType.KAKAO)
                    .socialId(subject).device(device).build());
            MeasurementSession session = persist(MeasurementSession.builder().user(user).device(device)
                    .measuredAt(LocalDateTime.now()).status(MeasurementStatus.MEASURING_PRESSURE).build());
            userId = user.getId();
            sessionId = session.getId();
            deviceId = device.getId();
            persist(HalluxValgusAnalysis.builder().measurementSession(session).riskScore(90f)
                    .leftImageUrl(PREFIX + privateKey).rightImageUrl(PREFIX + privateKey).build());
        });
    }

    @Test
    void hardDeletionRemovesEntireForeignKeyTreeButPreservesOtherMemberAndSharedCatalogs() throws Exception {
        Long otherId = tx.execute(ignored -> {
            Device device = em.find(Device.class, deviceId);
            User other = persist(User.builder().nickname("other").socialType(SocialType.KAKAO)
                    .socialId(UUID.randomUUID().toString()).device(device).build());
            User user = em.find(User.class, userId);
            seedPersonalData(user, em.find(MeasurementSession.class, sessionId));
            MeasurementSession otherSession = persist(MeasurementSession.builder().user(other).device(device)
                    .measuredAt(LocalDateTime.now()).status(MeasurementStatus.COMPLETED).build());
            seedPersonalData(other, otherSession);
            // A shared image must not be deleted while another member still references it.
            persist(HalluxValgusAnalysis.builder().measurementSession(otherSession).riskScore(80f)
                    .leftImageUrl(PREFIX + privateKey).build());
            return other.getId();
        });
        WebSocketSession targetSocket = socket("target-" + subject, userId);
        WebSocketSession otherSocket = socket("other-" + subject, otherId);

        var result = withdrawal.withdraw(userId, null);

        assertThat(result.isDeleted()).isTrue();
        assertThat(result.isCleanupPending()).isTrue();
        assertThat(result.getSocialUnlinkStatus()).isEqualTo("MANUAL_UNLINK_REQUIRED");
        assertThat(users.findById(userId)).isEmpty();
        assertThat(users.findById(otherId)).isPresent();
        assertThat(sessions.findIdsByUserId(userId)).isEmpty();
        assertThat(sessions.findIdsByUserId(otherId)).hasSize(1);
        tx.executeWithoutResult(ignored -> {
            assertThat(em.find(Device.class, deviceId).getStatus()).isEqualTo(DeviceStatus.REGISTERED);
            for (String table : List.of("user_terms", "user_foot_care_todo_assignment", "user_health_article",
                    "shoe_click_history", "shoe_search_history", "device_connection_log", "report", "shoe_recommendation")) {
                assertThat(count("select count(*) from " + table + " where user_id = " + userId)).isZero();
                assertThat(count("select count(*) from " + table + " where user_id = " + otherId)).isPositive();
            }
            assertThat(count("select count(*) from shoe")).isPositive();
            assertThat(count("select count(*) from shoe_review")).isPositive();
            assertThat(count("select count(*) from user_foot_care_todo")).isPositive();
            assertThat(count("select count(*) from health_article")).isPositive();
        });
        verify(targetSocket).close(CloseStatus.POLICY_VIOLATION);
        verify(otherSocket, never()).close(any());
        assertThat(tasks.findAll()).hasSize(2).allSatisfy(task ->
                assertThat(task.getEncryptedPayload()).doesNotContain(privateKey, "left-pressure", subject));

        processor().processReadyTasks();
        verify(images, never()).delete("test-bucket", privateKey);
        assertThat(tasks.count()).isZero();
        verifyNoInteractions(hardware);
    }

    @Test
    void databaseFailureRollsBackPersonalDeletionCleanupTasksAndSocketDisconnect() throws Exception {
        WebSocketSession targetSocket = socket("rollback-" + subject, userId);
        tx.executeWithoutResult(ignored -> {
            em.createNativeQuery("create table withdrawal_blocker (user_id bigint references users(id))").executeUpdate();
            em.createNativeQuery("insert into withdrawal_blocker values (:id)").setParameter("id", userId).executeUpdate();
        });
        try {
            assertThatThrownBy(() -> withdrawal.withdraw(userId, null)).isInstanceOf(RuntimeException.class);
            assertThat(users.findById(userId)).isPresent();
            assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(MeasurementStatus.MEASURING_PRESSURE);
            assertThat(tasks.count()).isZero();
            verify(targetSocket, never()).close(any());
            verifyNoInteractions(broker);
        } finally {
            tx.executeWithoutResult(ignored -> em.createNativeQuery("drop table withdrawal_blocker").executeUpdate());
        }
    }

    @Test
    void durableSocialCleanupSurvivesFailureAndBlocksRejoinUntilConfirmed() {
        when(social.prepare(any(), anyString(), any())).thenReturn(new SocialUnlinkClient.Prepared(
                AccountCleanupTask.Kind.KAKAO_UNLINK, new AccountCleanupStore.Payload(null, null, subject, "provider-token", false)));
        doThrow(new IllegalStateException("temporary provider failure")).doNothing().when(social).unlinkKakao(any());

        var result = withdrawal.withdraw(userId, null);
        assertThat(result.getSocialUnlinkStatus()).isEqualTo("PENDING");
        assertThat(users.findById(userId)).isEmpty();
        assertThatThrownBy(() -> cleanup.requireNoPendingUnlink(SocialType.KAKAO, subject)).isInstanceOf(GeneralException.class);
        DeviceStatus deviceStatus = tx.execute(ignored -> em.find(Device.class, deviceId).getStatus());
        assertThat(deviceStatus).isEqualTo(DeviceStatus.AVAILABLE);

        processor().processReadyTasks();
        var task = tasks.findAll().get(0);
        assertThat(task.getAttempts()).isOne();
        assertThat(task.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(task.getEncryptedPayload()).doesNotContain(subject, "provider-token");
        tx.executeWithoutResult(ignored -> em.createNativeQuery("update account_cleanup_task set next_attempt_at = :now")
                .setParameter("now", LocalDateTime.now().minusMinutes(1)).executeUpdate());
        processor().processReadyTasks();
        assertThat(tasks.count()).isZero();
        assertThatCode(() -> cleanup.requireNoPendingUnlink(SocialType.KAKAO, subject)).doesNotThrowAnyException();
        verify(social, times(2)).unlinkKakao(any());
    }

    @Test
    void delayedAiUploadCannotRecreateDeletedReportsAndItsOrphanImagesAreQueued() throws Exception {
        CountDownLatch uploading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(uploads.upload(anyString(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            uploading.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return UploadedImageTracker.record(ImageResponseDTO.UploadImageResultDTO.builder()
                    .imageUrl(PREFIX + call.getArgument(0) + "/late-" + subject + ".jpg").build());
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> late = worker.submit(() -> {
                var request = new MockHttpServletRequest("POST", "/api/reports/hallux-valgus");
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                try {
                    new ReportUploadCleanupFilter(cleanupProvider, deletionProvider).doFilter(request,
                            new MockHttpServletResponse(), (req, res) -> {
                                var body = new ReportRequestDTO.SaveHalluxValgusDTO();
                                ReflectionTestUtils.setField(body, "measurementSessionId", sessionId);
                                ReflectionTestUtils.setField(body, "leftToeAngleDegree", 10f);
                                ReflectionTestUtils.setField(body, "rightToeAngleDegree", 12f);
                                var photo = new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[]{1, 2});
                                reports.saveHalluxValgusAnalysis(userId, body, photo, photo);
                            });
                    throw new AssertionError("Late AI save should not succeed.");
                } catch (GeneralException expected) {
                    // The row was deleted while the external upload was still running.
                } catch (Exception unexpected) {
                    throw new AssertionError(unexpected);
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
            });
            assertThat(uploading.await(5, TimeUnit.SECONDS)).isTrue();
            withdrawal.withdraw(userId, null);
            release.countDown();
            late.get(5, TimeUnit.SECONDS);
            assertThat(users.findById(userId)).isEmpty();
            assertThat(sessions.findById(sessionId)).isEmpty();
            Long remaining = tx.execute(ignored -> count("select count(*) from hallux_valgus_analysis where measurement_session_id = " + sessionId));
            assertThat(remaining).isZero();
            assertThat(tasks.count()).isEqualTo(3);
            processor().processReadyTasks();
            assertThat(tasks.count()).isZero();
            verify(images, times(3)).delete(eq("test-bucket"), anyString());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void expiredLeaseCanBeReclaimedButStaleWorkerCannotCompleteNewClaim() {
        cleanup.enqueue(AccountCleanupTask.Kind.S3_IMAGE,
                new AccountCleanupStore.Payload("test-bucket", privateKey, null, null, false), null);
        Long id = tasks.findAll().get(0).getId();
        var old = cleanup.claim(id);
        assertThat(cleanup.claim(id)).isNull();
        tx.executeWithoutResult(ignored -> em.createNativeQuery("update account_cleanup_task set lease_until = :now where id = :id")
                .setParameter("now", LocalDateTime.now().minusMinutes(1)).setParameter("id", id).executeUpdate());
        var current = cleanup.claim(id);
        cleanup.complete(old);
        cleanup.retry(old);
        assertThat(tasks.findById(id).orElseThrow().getClaimToken()).isEqualTo(current.token());
        cleanup.complete(current);
        assertThat(tasks.findById(id)).isEmpty();
    }

    private void seedPersonalData(User user, MeasurementSession session) {
        LocalDateTime now = LocalDateTime.now();
        persist(MeasurementAnalysisStatus.builder().measurementSession(session).build());
        persist(DailyFootAnalysis.builder().measurementSession(session)
                .leftPressureImageUrl(PREFIX + "pressure-left/" + subject + ".jpg").build());
        persist(TinaPedisAnalysis.builder().measurementSession(session).fungalSuspicionSafetyScore(90)
                .skinReactionSafetyScore(90).fungalSuspicionSafetyDescription("fungal").skinReactionSafetyDescription("skin")
                .totalScoreDescription("total").suspiciousAreaMapImageUrl(PREFIX + privateKey)
                .originalFootImageUrl(PREFIX + privateKey).recordedAt(now).build());
        persist(PlantarFootprint.builder().measurementSession(session).plantarFootprintImageImage(PREFIX + privateKey).build());
        var reading = PressureSensorReading.builder().measurementSession(session).footSide(FootSide.LEFT).build();
        reading.addSensorValue(0, 200f);
        persist(reading);
        persist(FootEnvironmentReading.builder().measurementSession(session).footSide(FootSide.LEFT).build());
        persist(FootOdorReading.builder().measurementSession(session).footSide(FootSide.LEFT).build());
        persist(StaticPressureAnalysis.builder().measurementSession(session).footSide(FootSide.LEFT)
                .centerOfPressureX(1f).centerOfPressureY(2f).balanceScore(80f).balanceStatus(GaugeStatus.VERY_GOOD).build());
        persist(FootEnvironmentAnalysis.builder().measurementSession(session).footSide(FootSide.LEFT)
                .avgTemperatureCelsius(25f).avgHumidityPercent(40f).avgOdorValue(1f).temperatureScore(90f)
                .humidityScore(90f).odorScore(90f).environmentScore(EnvironmentScore.NORMAL)
                .temperatureGaugeStatus(GaugeStatus.VERY_GOOD).humidityGaugeStatus(GaugeStatus.VERY_GOOD).recordedAt(now).build());
        persist(FootOdorAnalysis.builder().measurementSession(session).footSide(FootSide.LEFT).odorLevel(OdorLevel.LOW).riskScore(90f).build());
        var report = persist(Report.builder().user(user).measurementSession(session).reportDate(now).totalScore(90).build());
        persist(MetricAnalysisResult.builder().report(report).metricType(MetricType.HALLUX_VALGUS).score(90f)
                .status(GaugeStatus.VERY_GOOD).advice(List.of("advice")).build());
        var drugs = DrugRecommendation.builder().report(report).build();
        drugs.prepare("{}", "summary", "hash", "model");
        persist(drugs);
        Shoe shoe = persist(Shoe.builder().brandName("brand").shoeName("shoe").modelCode(UUID.randomUUID().toString())
                .musinsaGoodsNo(UUID.randomUUID().toString()).musinsaUrl("https://example.com/shoe").build());
        ShoeReview review = persist(ShoeReview.builder().shoe(shoe).rating(5f).reviewText("shared review")
                .sourceReviewId(UUID.randomUUID().toString()).contentHash(UUID.randomUUID().toString())
                .source(ShoeReviewSource.MUSINSA).collectedAt(now).build());
        var recommendation = persist(ShoeRecommendation.builder().user(user).measurementSession(session).shoe(shoe)
                .fitScore(90f).analyzedAt(now).build());
        var reason = persist(ShoeRecommendationReason.builder().shoeRecommendation(recommendation).reasonType(ReasonType.HEEL)
                .title("heel").riskLevel(RiskLevel.LOW).build());
        persist(ShoeRecommendationReasonReview.builder().reason(reason).review(review).build());
        persist(ShoeRecommendationRun.builder().measurementSession(session).expectedCount(1).build());
        persist(UserTerms.builder().user(user).isAgreed(true).agreedAt(now).expiresAt(now.plusYears(1)).build());
        var todo = persist(UserFootCareTodo.builder().title("care").healthType(HealthType.HALLUX_VALGUS).todoDate(now).build());
        persist(UserFootCareTodoAssignment.builder().user(user).footCareTodo(todo).sourceMeasurementSessionId(session.getId()).build());
        var article = persist(HealthArticle.builder().title("article").url("https://example.com/article").publisher("publisher").publishedAt(now).build());
        persist(UserHealthArticle.builder().user(user).healthArticle(article).build());
        persist(ShoeClickHistory.builder().user(user).shoe(shoe).build());
        persist(ShoeSearchHistory.builder().user(user).keyword("shoe").searchedAt(now).expiresAt(now.plusDays(7)).build());
        persist(DeviceConnectionLog.connected(user, session.getDevice(), ConnectionType.BLUETOOTH, now));
    }

    private WebSocketSession socket(String id, Long memberId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        when(socket.isOpen()).thenReturn(true);
        sockets.connected(socket);
        sockets.authenticate(id, memberId);
        return socket;
    }

    private <T> T persist(T entity) { em.persist(entity); return entity; }
    private long count(String sql) { return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue(); }
    private AccountCleanupProcessor processor() { return new AccountCleanupProcessor(tasks, cleanup, images, references, social); }
}
