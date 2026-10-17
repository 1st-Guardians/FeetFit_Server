package com.feetfit.server.service.DrugService;

import com.feetfit.server.config.AsyncConfig;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.*;
import com.feetfit.server.service.MeasurementService.MeasurementCareTipsGenerationService;
import com.feetfit.server.service.MeasurementService.MeasurementCompletionService;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({JacksonAutoConfiguration.class, AsyncConfig.class, DrugCatalogMapper.class,
        DrugRecommendationPolicy.class, DrugRecommendationStore.class, DrugRecommendationQueryService.class,
        DrugRecommendationGenerationService.class, DrugRecommendationListener.class,
        MeasurementCompletionService.class, MeasurementSocketService.class})
class DrugRecommendationCompletionJpaIntegrationTest {
    @Autowired TestEntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MeasurementCompletionService completion;
    @Autowired DrugRecommendationQueryService query;
    @Autowired @Qualifier("drugRecommendationTaskExecutor") Executor recommendationExecutor;
    @MockBean DrugRecommendationSummaryClient client;
    @MockBean SimpMessagingTemplate messaging;
    @MockBean MeasurementCareTipsGenerationService careTips;
    private TransactionTemplate transactions;

    @BeforeEach
    void setup() {
        transactions = new TransactionTemplate(transactionManager);
        when(client.model()).thenReturn(DrugRecommendationSummaryClient.DEFAULT_MODEL);
    }

    @Test
    void frontendReceivesCompletionBeforeGptStartsAndSummaryIsSavedLater() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.generate(any())).thenAnswer(call -> {
            verify(messaging, times(2)).convertAndSend(anyString(), any(MeasurementResponseDTO.MeasurementSocketMessageDTO.class));
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return "Persisted GPT explanation";
        });
        Fixture fixture;
        try {
            fixture = completeReport();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertCompletionSent(fixture);
            assertThat(query.get(fixture.userId(), null, null, 0, 20).getSummaryStatus()).isEqualTo("GENERATING");
        } finally {
            release.countDown();
        }
        awaitRecommendationTasks();
        assertCompletionSent(fixture);
        var result = query.get(fixture.userId(), null, null, 0, 20);
        assertThat(result.getSummary()).isEqualTo("Persisted GPT explanation");
        assertThat(result.getSummaryStatus()).isEqualTo("COMPLETED");
        assertThat(result.getSummarySource()).isEqualTo("GPT");
        assertThat(result.getPriorities()).hasSize(3);
        verify(client).generate(any());
    }

    @Test
    void failedGenerationKeepsCompletedReportWithoutAnotherCompletionSocket() throws Exception {
        when(client.generate(any())).thenThrow(new IllegalStateException("API unavailable"));
        var fixture = completeReport();

        awaitRecommendationTasks();
        assertCompletionSent(fixture);
        var result = query.get(fixture.userId(), null, null, 0, 20);
        assertThat(result.isHasMeasurement()).isTrue();
        assertThat(result.getSummaryStatus()).isEqualTo("FAILED");
        assertThat(result.getSummarySource()).isEqualTo("RULE_BASED");
        assertThat(result.getSummary()).isNotBlank();
    }

    @Test
    void rejectedExecutorDoesNotBlockCompletionAndRecommendationCanBeRetried() throws Exception {
        var executor = (ThreadPoolTaskExecutor) recommendationExecutor;
        Fixture fixture;
        executor.shutdown();
        try {
            fixture = completeReport();
            assertCompletionSent(fixture);
            assertThat(query.get(fixture.userId(), null, null, 0, 20).getSummaryStatus()).isEqualTo("NOT_READY");
            verify(client, never()).generate(any());
        } finally {
            executor.initialize();
        }
        when(client.generate(any())).thenReturn("Retried GPT explanation");
        query.refresh(fixture.userId());
        awaitRecommendationTasks();

        assertCompletionSent(fixture);
        var result = query.get(fixture.userId(), null, null, 0, 20);
        assertThat(result.getSummaryStatus()).isEqualTo("COMPLETED");
        assertThat(result.getSummary()).isEqualTo("Retried GPT explanation");
    }

    @Test
    void queuedRecommendationDoesNotPrepareOrderingUntilAfterCompletion() throws Exception {
        var occupied = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        recommendationExecutor.execute(() -> {
            occupied.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        when(client.generate(any())).thenReturn("Background GPT explanation");
        Fixture fixture;
        try {
            fixture = completeReport();
            assertCompletionSent(fixture);
            var result = query.get(fixture.userId(), null, null, 0, 20);
            assertThat(result.isHasMeasurement()).isTrue();
            assertThat(result.getSummaryStatus()).isEqualTo("NOT_READY");
            assertThat(result.getSummary()).isNull();
            assertThat(result.getPriorities()).isEmpty();
            verifyNoInteractions(client);
        } finally {
            release.countDown();
        }
        awaitRecommendationTasks();
        assertCompletionSent(fixture);
        assertThat(query.get(fixture.userId(), null, null, 0, 20).getSummaryStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void recommendationPreparationFailureCannotRollBackMeasurement() throws Exception {
        when(client.model()).thenThrow(new IllegalStateException("Recommendation configuration unavailable"));

        var fixture = completeReport();
        awaitRecommendationTasks();

        assertCompletionSent(fixture);
        var result = query.get(fixture.userId(), null, null, 0, 20);
        assertThat(result.isHasMeasurement()).isTrue();
        assertThat(result.getReportId()).isEqualTo(fixture.reportId());
        assertThat(result.getSummaryStatus()).isEqualTo("NOT_READY");
        verify(client, never()).generate(any());
    }

    @Test
    void rollbackDoesNotPublishCompletionOrStartRecommendation() throws Exception {
        var fixture = completeReport(true);
        awaitRecommendationTasks();

        verifyNoInteractions(messaging, client);
        assertThat(query.get(fixture.userId(), null, null, 0, 20).isHasMeasurement()).isFalse();
    }

    private Fixture completeReport() {
        return completeReport(false);
    }

    private Fixture completeReport(boolean rollback) {
        return transactions.execute(tx -> {
            var user = em.persist(User.builder().nickname("notification-test").socialId(UUID.randomUUID().toString())
                    .socialType(SocialType.KAKAO).status(UserStatus.ACTIVE).build());
            var device = em.persist(Device.builder().deviceName("notification-" + UUID.randomUUID()).build());
            var session = em.persist(MeasurementSession.builder().user(user).device(device)
                    .status(MeasurementStatus.ANALYZING).measuredAt(LocalDateTime.now()).build());
            var report = em.persist(Report.builder().user(user).measurementSession(session)
                    .reportDate(LocalDateTime.now()).totalScore(80).build());
            for (var type : List.of(MetricType.ATHLETES_FOOT, MetricType.SKIN_IRRITATION)) {
                em.persist(MetricAnalysisResult.builder().report(report).metricType(type)
                        .score(type == MetricType.ATHLETES_FOOT ? 35f : 80f)
                        .status(GaugeStatus.ATTENTION_NEEDED).advice(List.of("test")).build());
            }
            em.persist(HalluxValgusAnalysis.builder().measurementSession(session)
                    .leftToeAngleDegree(18f).rightToeAngleDegree(12f).riskScore(50f).build());
            em.persist(MeasurementAnalysisStatus.builder().measurementSession(session)
                    .photoCaptureCompleted(true).photoAnalysisCompleted(true)
                    .pressureCaptureCompleted(true).pressureAnalysisCompleted(true)
                    .environmentAnalysisCompleted(true).metricReportCompleted(true).build());
            em.flush();
            completion.completeMeasurementIfReady(session, 180);
            assertThat(session.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
            assertThat(em.find(DrugRecommendation.class, report.getId())).isNull();
            verifyNoInteractions(messaging, client);
            if (rollback) {
                tx.setRollbackOnly();
            }
            return new Fixture(user.getId(), session.getId(), report.getId());
        });
    }

    private void assertCompletionSent(Fixture fixture) {
        var messages = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(messaging).convertAndSend(eq(fixture.topic()), messages.capture());
        var message = messages.getValue();
        assertThat(message.getEventType()).isEqualTo("MEASUREMENT_COMPLETED");
        assertThat(message.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        assertThat(message.getReportId()).isEqualTo(fixture.reportId());
        assertThat(message.getDrugRecommendationStatus()).isEqualTo("PENDING");
        assertThat(message.getDrugRecommendationSummarySource()).isNull();
        assertThat(message.getShouldDisconnect()).isTrue();
        verify(messaging).convertAndSend("/topic/users/" + fixture.userId() + "/measurements", message);
        verifyNoMoreInteractions(messaging);
    }

    private void awaitRecommendationTasks() throws InterruptedException {
        var drained = new CountDownLatch(1);
        recommendationExecutor.execute(drained::countDown);
        assertThat(drained.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private record Fixture(Long userId, Long sessionId, Long reportId) {
        String topic() { return "/topic/measurements/" + sessionId; }
    }
}
