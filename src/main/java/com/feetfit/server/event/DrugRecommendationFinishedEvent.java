package com.feetfit.server.event;

public record DrugRecommendationFinishedEvent(Long reportId, String inputHash, boolean generated) {
}
