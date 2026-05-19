package com.feetfit.server.repository;

import com.feetfit.server.domain.HealthArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthArticleRepository extends JpaRepository<HealthArticle, Long> {
}
