package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasurementWriteTransactionsTest {
    @Mock PlatformTransactionManager manager;
    @Mock EntityManager entityManager;
    private MeasurementWriteTransactions writes;

    @BeforeEach
    void setup() {
        writes = new MeasurementWriteTransactions(manager, entityManager);
        lenient().when(manager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThat(definition.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return new SimpleTransactionStatus();
        });
    }

    @Test
    void requiredWritesJoinTheCallerTransactionWithoutClearingItsEntities() {
        doAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
            return new SimpleTransactionStatus();
        }).when(manager).getTransaction(any());
        assertThat(writes.executeRequired(() -> "saved")).isEqualTo("saved");
        verify(manager).commit(any());
        verifyNoInteractions(entityManager);
    }

    @Test
    void lockConflictsRollbackAndRetryTheEntireWriteWithFreshEntities() {
        AtomicInteger calls = new AtomicInteger();
        String result = writes.execute(73L, () -> {
            if (calls.incrementAndGet() < 3) throw new CannotAcquireLockException("busy");
            return "saved";
        });

        assertThat(result).isEqualTo("saved");
        verify(manager, times(2)).rollback(any());
        verify(manager).commit(any());
        verify(entityManager, times(3)).clear();
    }

    @Test
    void exhaustedLockConflictsReturnRetryable503InsteadOfPretendingSuccess() {
        assertThatThrownBy(() -> writes.execute(73L, () -> { throw new CannotAcquireLockException("busy"); }))
                .isInstanceOf(MeasurementHandler.class)
                .satisfies(error -> assertThat(((MeasurementHandler) error).getCode())
                        .isEqualTo(ErrorStatus.MEASUREMENT_WRITE_UNAVAILABLE));
        verify(manager, times(3)).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void staleAttemptIsNotRetried() {
        assertThatThrownBy(() -> writes.execute(73L, () -> {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);
        })).isInstanceOf(MeasurementHandler.class);
        verify(manager).getTransaction(any());
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }
}
