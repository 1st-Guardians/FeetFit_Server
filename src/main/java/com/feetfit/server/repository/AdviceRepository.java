package com.feetfit.server.repository;

import com.feetfit.server.domain.Advice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdviceRepository extends JpaRepository<Advice, Long> {
}
