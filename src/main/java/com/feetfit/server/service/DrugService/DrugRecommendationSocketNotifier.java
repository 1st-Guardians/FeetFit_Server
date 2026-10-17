package com.feetfit.server.service.DrugService;

import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.event.DrugRecommendationFinishedEvent;
import com.feetfit.server.repository.DrugRecommendationRepository;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Component
@RequiredArgsConstructor
public class DrugRecommendationSocketNotifier {
    private final DrugRecommendationRepository recommendations;
    private final MeasurementSocketService socket;

    @EventListener
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void notifyFinished(DrugRecommendationFinishedEvent event) {
        var saved = recommendations.findById(event.reportId()).orElse(null);
        if (saved == null || !saved.getInputHash().equals(event.inputHash())
                || !saved.getStatus().equals(event.generated() ? "COMPLETED" : "FAILED")) {
            return;
        }
        var session = saved.getReport().getMeasurementSession();
        if (session.getStatus() == MeasurementStatus.COMPLETED) {
            socket.sendMeasurementRecommendationFinished(session, event.reportId(), event.generated());
        }
    }
}
