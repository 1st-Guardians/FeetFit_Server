package com.feetfit.server.service.DrugService;

import com.feetfit.server.domain.DrugRecommendation;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.event.DrugRecommendationFinishedEvent;
import com.feetfit.server.repository.DrugRecommendationRepository;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

class DrugRecommendationSocketNotifierTest {
    private final DrugRecommendationRepository recommendations = mock(DrugRecommendationRepository.class);
    private final MeasurementSocketService socket = mock(MeasurementSocketService.class);
    private final DrugRecommendationSocketNotifier notifier = new DrugRecommendationSocketNotifier(recommendations, socket);

    @Test
    void sendsCompletionOnlyForPersistedMatchingGptResult() {
        var session = record("COMPLETED", MeasurementStatus.COMPLETED);
        notifier.notifyFinished(new DrugRecommendationFinishedEvent(8L, "current-hash", true));
        verify(socket).sendMeasurementRecommendationFinished(session, 8L, true);
    }

    @Test
    void failedSummarySendsWarningInsteadOfSuccess() {
        var session = record("FAILED", MeasurementStatus.COMPLETED);
        notifier.notifyFinished(new DrugRecommendationFinishedEvent(8L, "current-hash", false));
        verify(socket).sendMeasurementRecommendationFinished(session, 8L, false);
    }

    @Test
    void ignoresStaleResultOrUncommittedSummaryOrFailedMeasurement() {
        record("COMPLETED", MeasurementStatus.COMPLETED);
        notifier.notifyFinished(new DrugRecommendationFinishedEvent(8L, "old-hash", true));
        record("GENERATING", MeasurementStatus.COMPLETED);
        notifier.notifyFinished(new DrugRecommendationFinishedEvent(8L, "current-hash", true));
        record("COMPLETED", MeasurementStatus.FAILED);
        notifier.notifyFinished(new DrugRecommendationFinishedEvent(8L, "current-hash", true));
        verifyNoInteractions(socket);
    }

    private MeasurementSession record(String summaryStatus, MeasurementStatus status) {
        var session = MeasurementSession.builder().id(43L).status(status).build();
        var saved = DrugRecommendation.builder().reportId(8L)
                .report(Report.builder().measurementSession(session).build())
                .inputHash("current-hash").status(summaryStatus).build();
        when(recommendations.findById(8L)).thenReturn(Optional.of(saved));
        return session;
    }
}
