package com.feetfit.server.repository;

import com.feetfit.server.domain.UserFootCareTodo;
import com.feetfit.server.domain.enums.HealthType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface UserFootCareTodoRepository extends JpaRepository<UserFootCareTodo, Long> {
    List<UserFootCareTodo> findByHealthTypeInAndTodoDateGreaterThanEqualAndTodoDateLessThanOrderByIdAsc(
            Collection<HealthType> healthTypes,
            LocalDateTime startOfDay,
            LocalDateTime startOfNextDay
    );

    List<UserFootCareTodo> findByHealthTypeInOrderByIdAsc(Collection<HealthType> healthTypes);
}
