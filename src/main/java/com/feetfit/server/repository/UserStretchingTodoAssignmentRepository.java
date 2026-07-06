package com.feetfit.server.repository;

import com.feetfit.server.domain.UserStretchingTodoAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserStretchingTodoAssignmentRepository extends JpaRepository<UserStretchingTodoAssignment, Long> {

    @Query("""
            SELECT assignment
            FROM UserStretchingTodoAssignment assignment
            JOIN FETCH assignment.stretchingTodo todo
            WHERE assignment.user.id = :userId
              AND assignment.createdAt >= :startOfDay
              AND assignment.createdAt < :startOfNextDay
            ORDER BY assignment.createdAt ASC
            """)
    List<UserStretchingTodoAssignment> findTodayAssignmentsByUserId(
            @Param("userId") Long userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("startOfNextDay") LocalDateTime startOfNextDay
    );

    @Query("""
            SELECT assignment
            FROM UserStretchingTodoAssignment assignment
            JOIN FETCH assignment.stretchingTodo todo
            WHERE assignment.user.id = :userId
              AND todo.id = :todoId
            """)
    Optional<UserStretchingTodoAssignment> findByUserIdAndTodoId(
            @Param("userId") Long userId,
            @Param("todoId") Long todoId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserStretchingTodoAssignment assignment
            WHERE assignment.user.id = :userId
              AND assignment.createdAt >= :startOfDay
              AND assignment.createdAt < :startOfNextDay
            """)
    void deleteByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("startOfNextDay") LocalDateTime startOfNextDay
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserStretchingTodoAssignment assignment
            WHERE assignment.user.id = :userId
              AND assignment.stretchingTodo.id IN :todoIds
            """)
    void deleteByUserIdAndTodoIdIn(
            @Param("userId") Long userId,
            @Param("todoIds") Collection<Long> todoIds
    );
}
