package com.feetfit.server.repository;

import com.feetfit.server.domain.UserStretchingTodo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStretchingTodoRepository extends JpaRepository<UserStretchingTodo, Long> {
}
