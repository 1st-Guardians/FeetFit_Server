package com.feetfit.server.repository;

import com.feetfit.server.domain.AccountCleanupTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountCleanupTaskRepository extends JpaRepository<AccountCleanupTask, Long> {
    @Query("""
            select task.id from AccountCleanupTask task
            where task.nextAttemptAt <= :now and (task.leaseUntil is null or task.leaseUntil <= :now)
            order by case when task.identityHash is null then 1 else 0 end, task.id
            """)
    List<Long> findReadyIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AccountCleanupTask task where task.id = :id")
    Optional<AccountCleanupTask> findByIdForUpdate(@Param("id") Long id);

    boolean existsByIdentityHash(String identityHash);

    @Modifying
    @Query("delete from AccountCleanupTask task where task.id = :id and task.claimToken = :token")
    int complete(@Param("id") Long id, @Param("token") String token);
}
