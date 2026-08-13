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

    @Query("""
            SELECT assignment
            FROM UserFootCareTodoAssignment assignment
            JOIN FETCH assignment.footCareTodo todo
            WHERE assignment.user.id = :userId
              AND assignment.createdAt >= :startOfDay
              AND assignment.createdAt < :startOfNextDay
            ORDER BY assignment.createdAt ASC
            """)
    List<UserFootCareTodoAssignment> findTodayAssignmentsByUserId(
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
              AND assignment.createdAt >= :startOfDay
              AND assignment.createdAt < :startOfNextDay
            """)
    void deleteByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
}
