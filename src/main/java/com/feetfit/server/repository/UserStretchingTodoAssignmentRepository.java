package com.feetfit.server.repository;

import com.feetfit.server.domain.UserStretchingTodoAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserStretchingTodoAssignmentRepository extends JpaRepository<UserStretchingTodoAssignment, Long> {

    @Query("""
            SELECT assignment
            FROM UserStretchingTodoAssignment assignment
            JOIN FETCH assignment.stretchingTodo todo
            WHERE assignment.user.id = :userId
              AND todo.todoDate >= :startOfDay
              AND todo.todoDate < :startOfNextDay
            ORDER BY todo.todoDate ASC
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
}
