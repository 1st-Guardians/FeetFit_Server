package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeSearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoeSearchHistoryRepository extends JpaRepository<ShoeSearchHistory, Long> {
}
