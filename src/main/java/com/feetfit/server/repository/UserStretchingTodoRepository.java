package com.feetfit.server.repository;

import com.feetfit.server.domain.UserStretchingTodo;
import com.feetfit.server.domain.enums.HealthType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface UserStretchingTodoRepository extends JpaRepository<UserStretchingTodo, Long> {
    List<UserStretchingTodo> findByHealthTypeInAndTodoDateGreaterThanEqualAndTodoDateLessThanOrderByIdAsc(
            Collection<HealthType> healthTypes,
            LocalDateTime startOfDay,
            LocalDateTime startOfNextDay
    );

    List<UserStretchingTodo> findByHealthTypeInOrderByIdAsc(Collection<HealthType> healthTypes);
}
