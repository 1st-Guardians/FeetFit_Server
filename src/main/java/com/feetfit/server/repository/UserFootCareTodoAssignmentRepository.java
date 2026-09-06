package com.feetfit.server.repository;

import com.feetfit.server.domain.UserFootCareTodoAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserFootCareTodoAssignmentRepository extends JpaRepository<UserFootCareTodoAssignment, Long> {

    Optional<UserFootCareTodoAssignment> findTopByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    default List<UserFootCareTodoAssignment> findLatestAssignmentsByUserId(Long userId) {
        return findTopByUserIdOrderByCreatedAtDescIdDesc(userId)
                .map(latest -> {
                    if (latest.getSourceMeasurementSessionId() != null) {
                        return findByUserIdAndSourceMeasurementSessionId(userId, latest.getSourceMeasurementSessionId());
                    }
                    // Legacy assignments have no source session and accumulated across days.
                    LocalDateTime start = latest.getCreatedAt().toLocalDate().atStartOfDay();
                    return findAssignmentsByUserIdAndAssignedDate(userId, start, start.plusDays(1));
                })
                .orElseGet(List::of);
    }

    @Query("""
            SELECT assignment FROM UserFootCareTodoAssignment assignment
            JOIN FETCH assignment.footCareTodo
            WHERE assignment.user.id = :userId
              AND assignment.sourceMeasurementSessionId = :measurementSessionId
            ORDER BY assignment.createdAt ASC, assignment.id ASC
            """)
    List<UserFootCareTodoAssignment> findByUserIdAndSourceMeasurementSessionId(
            @Param("userId") Long userId,
            @Param("measurementSessionId") Long measurementSessionId);

    @Query("""
            SELECT assignment
            FROM UserFootCareTodoAssignment assignment
            JOIN FETCH assignment.footCareTodo todo
            WHERE assignment.user.id = :userId
              AND assignment.createdAt >= :startOfDay
              AND assignment.createdAt < :startOfNextDay
            ORDER BY assignment.createdAt ASC, assignment.id ASC
            """)
    List<UserFootCareTodoAssignment> findAssignmentsByUserIdAndAssignedDate(
            @Param("userId") Long userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("startOfNextDay") LocalDateTime startOfNextDay
    );

    @Query("""
            SELECT assignment
            FROM UserFootCareTodoAssignment assignment
            JOIN FETCH assignment.footCareTodo todo
            WHERE assignment.user.id = :userId
              AND todo.id = :todoId
            """)
    Optional<UserFootCareTodoAssignment> findByUserIdAndTodoId(
            @Param("userId") Long userId,
            @Param("todoId") Long todoId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserFootCareTodoAssignment assignment
            WHERE assignment.user.id = :userId
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
