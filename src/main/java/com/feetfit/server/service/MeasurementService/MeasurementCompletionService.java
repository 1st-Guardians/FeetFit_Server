package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurementCompletionService {

    private final MeasurementSessionRepository measurementSessionRepository;
    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final MeasurementSocketService measurementSocketService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeIfRequiredAnalysesSaved(Long measurementSessionId) {
        boolean hasHalluxValgus = halluxValgusAnalysisRepository.existsByMeasurementSessionId(measurementSessionId);
        boolean hasTinaPedis = tinaPedisAnalysisRepository.existsByMeasurementSessionId(measurementSessionId);

        MeasurementSession measurementSession = measurementSessionRepository.findByIdForCompletion(measurementSessionId)
                .orElse(null);
        if (measurementSession == null) {
            log.warn("Measurement completion skipped. measurementSessionId={} not found", measurementSessionId);
            return;
        }

        log.info("Measurement completion check. measurementSessionId={}, status={}, hasHalluxValgus={}, hasTinaPedis={}",
                measurementSessionId,
                measurementSession.getStatus(),
                hasHalluxValgus,
                hasTinaPedis
        );

        if (hasHalluxValgus && hasTinaPedis && measurementSession.getStatus() != MeasurementStatus.COMPLETED) {
            measurementSession.updateStatus(MeasurementStatus.COMPLETED, measurementSession.getMeasurementDurationSec());
            measurementSocketService.sendMeasurementCompleted(measurementSession);
        }
    }
}
