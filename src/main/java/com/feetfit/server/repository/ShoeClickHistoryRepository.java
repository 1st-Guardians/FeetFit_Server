package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeClickHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoeClickHistoryRepository extends JpaRepository<ShoeClickHistory, Long> {
    boolean existsByUserIdAndShoeId(Long userId, Long shoeId);
}