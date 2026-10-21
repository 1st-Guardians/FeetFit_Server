package com.feetfit.server.service.ImageService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImageReferenceService {
    private final EntityManager em;

    @Transactional(readOnly = true)
    public boolean isReferenced(Set<String> urls) {
        return count("select count(a) from HalluxValgusAnalysis a where a.leftImageUrl in :urls or a.rightImageUrl in :urls", urls)
                || count("""
                select count(a) from TinaPedisAnalysis a where a.originalFootImageUrl in :urls
                or a.suspiciousAreaMapImageUrl in :urls or a.soleOriginalFootImageUrl in :urls
                or a.soleSuspiciousAreaMapImageUrl in :urls
                """, urls)
                || count("""
                select count(a) from DailyFootAnalysis a where a.leftPressureImageUrl in :urls or a.rightPressureImageUrl in :urls
                or a.leftPressureHeatmapImageUrl in :urls or a.rightPressureHeatmapImageUrl in :urls
                or a.leftPlantarFootprintImageUrl in :urls or a.rightPlantarFootprintImageUrl in :urls
                """, urls)
                || count("select count(a) from PlantarFootprint a where a.plantarFootprintImageImage in :urls", urls);
    }

    private boolean count(String query, Set<String> urls) {
        return em.createQuery(query, Long.class).setParameter("urls", urls).getSingleResult() > 0;
    }
}
