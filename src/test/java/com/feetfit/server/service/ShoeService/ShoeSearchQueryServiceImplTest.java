package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import com.feetfit.server.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoeSearchQueryServiceImplTest {

    @Mock ShoeRepository shoeRepository;
    @Mock ShoeSearchHistoryRepository shoeSearchHistoryRepository;
    @Mock UserRepository userRepository;
    @Mock ShoeRecommendationReasonRepository reasonRepository;
    @Mock ShoeRecommendationRepository recommendationRepository;
    @Mock ShoeRecommendationSessionResolver sessionResolver;
    @InjectMocks ShoeSearchQueryServiceImpl service;

    @Test
    void explicitCompletedSessionReturnsItsPendingDetailSnapshot() {
        Shoe shoe = shoe();
        ShoeRecommendation pending = recommendation(shoe, null);
        when(shoeRepository.findById(11L)).thenReturn(Optional.of(shoe));
        when(sessionResolver.requireCompleted(7L, 21L))
                .thenReturn(new ShoeRecommendationSessionResolver.ResolvedRecommendationSession(
                        31L, 21L, 7L));
        when(recommendationRepository.findByMeasurementSessionIdAndShoeId(21L, 11L))
                .thenReturn(Optional.of(pending));
        when(reasonRepository.findDetailByShoeRecommendationId(41L))
                .thenReturn(reasons(pending, null));

        var result = service.getShoeDetail(7L, 11L, 21L);

        assertThat(result.getMeasurementSessionId()).isEqualTo(21L);
        assertThat(result.getPointSummary()).isNull();
        assertThat(result.getReasons()).hasSize(3);
        verify(sessionResolver, never()).resolveCurrentCompleted(anyLong());
    }

    @Test
    void currentCompletedSessionReturnsItsCompleteSummary() {
        Shoe shoe = shoe();
        ShoeRecommendation recommendation = recommendation(shoe, "point summary");
        when(shoeRepository.findById(11L)).thenReturn(Optional.of(shoe));
        when(sessionResolver.resolveCurrentCompleted(7L))
                .thenReturn(Optional.of(
                        new ShoeRecommendationSessionResolver.ResolvedRecommendationSession(
                                32L, 22L, 7L)));
        when(recommendationRepository.findByMeasurementSessionIdAndShoeId(22L, 11L))
                .thenReturn(Optional.of(recommendation));
        when(reasonRepository.findDetailByShoeRecommendationId(41L))
                .thenReturn(reasons(recommendation, "review summary"));

        var result = service.getShoeDetail(7L, 11L, null);

        assertThat(result.getMeasurementSessionId()).isEqualTo(22L);
        assertThat(result.getPointSummary()).isEqualTo("point summary");
    }

    @Test
    void guestDetailSkipsRecommendationScope() {
        Shoe shoe = shoe();
        when(shoeRepository.findById(11L)).thenReturn(Optional.of(shoe));

        var result = service.getShoeDetail(null, 11L, null);

        assertThat(result.getMeasurementSessionId()).isNull();
        assertThat(result.getFitScore()).isNull();
        assertThat(result.getPointSummary()).isNull();
        verifyNoInteractions(sessionResolver, recommendationRepository, reasonRepository);
    }

    private static Shoe shoe() {
        return Shoe.builder()
                .id(11L)
                .brandName("브랜드")
                .shoeName("신발")
                .modelCode("MODEL")
                .musinsaUrl("https://example.com")
                .build();
    }

    private static ShoeRecommendation recommendation(Shoe shoe, String pointSummary) {
        return ShoeRecommendation.builder()
                .id(41L)
                .shoe(shoe)
                .fitScore(88f)
                .pointSummary(pointSummary)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private static java.util.List<ShoeRecommendationReason> reasons(
            ShoeRecommendation recommendation, String reviewSummary) {
        return Arrays.stream(ReasonType.values())
                .map(type -> ShoeRecommendationReason.builder()
                        .shoeRecommendation(recommendation)
                        .reasonType(type)
                        .title(type.name())
                        .riskLevel(RiskLevel.LOW)
                        .reviewSummary(reviewSummary)
                        .build())
                .toList();
    }
}
