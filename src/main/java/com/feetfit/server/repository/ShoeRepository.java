package com.feetfit.server.repository;

import com.feetfit.server.domain.Shoe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoeRepository extends JpaRepository<Shoe, Long> {
}
