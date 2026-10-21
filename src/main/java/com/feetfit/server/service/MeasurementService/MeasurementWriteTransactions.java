package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeasurementWriteTransactions {
    private static final int MAX_ATTEMPTS = 3;

    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    public <T> T executeRequired(Supplier<T> operation) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction.execute(ignored -> operation.get());
    }

    public <T> T execute(Long measurementSessionId, Supplier<T> operation) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.execute(ignored -> {
                    // A retried write must never reuse an entity loaded before acquiring its lock.
                    entityManager.clear();
                    return operation.get();
                });
            } catch (PessimisticLockingFailureException failure) {
                log.warn("Measurement write lock conflict. measurementSessionId={}, attempt={}, errorType={}",
                        measurementSessionId, attempt, failure.getClass().getSimpleName());
                if (attempt == MAX_ATTEMPTS) {
                    throw new MeasurementHandler(ErrorStatus.MEASUREMENT_WRITE_UNAVAILABLE);
                }
            }
        }
        throw new IllegalStateException("Measurement write retry exhausted");
    }
}
