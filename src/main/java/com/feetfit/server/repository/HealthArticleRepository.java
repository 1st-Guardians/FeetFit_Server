package com.feetfit.server.repository;

import com.feetfit.server.domain.HealthArticle;
import com.feetfit.server.domain.enums.HealthType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HealthArticleRepository extends JpaRepository<HealthArticle, Long> {

    List<HealthArticle> findByHealthTypeInOrderByPublishedAtDesc(Collection<HealthType> healthTypes);
}
