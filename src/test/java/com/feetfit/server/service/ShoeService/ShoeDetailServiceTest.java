package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeDetailServiceTest {

    @Mock ShoeSearchQueryService queryService;
    @Mock ShoeSummaryGenerationTrigger summaryGenerationTrigger;
    @InjectMocks ShoeDetailService service;

    @Test
    void orchestrationExplicitlySuspendsAnyCallerTransaction() throws Exception {
        Transactional transactional = ShoeDetailService.class
                .getMethod("getShoeDetail", Long.class, Long.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void firstRequestReturnsFreshCompleteSummaryAfterGeneration() {
        ShoeResponseDTO.ShoeDetailResultDTO pending = detail(21L, 88f, null, null);
        ShoeResponseDTO.ShoeDetailResultDTO completed = detail(
                21L, 88f, "자연스러운 착용 요약", "자연스러운 리뷰 요약");
        when(queryService.getShoeDetail(7L, 11L, null)).thenReturn(pending);
        when(summaryGenerationTrigger.generateNow(7L, 21L, 11L)).thenReturn(true);
        when(queryService.getShoeDetail(7L, 11L, 21L)).thenReturn(completed);

        ShoeResponseDTO.ShoeDetailResultDTO result = service.getShoeDetail(7L, 11L, null);

        assertThat(result.getPointSummary()).isEqualTo("자연스러운 착용 요약");
        assertThat(result.getReasons()).hasSize(3)
                .allSatisfy(reason -> assertThat(reason.getReviewSummary())
                        .isEqualTo("자연스러운 리뷰 요약"));
        verify(queryService).getShoeDetail(7L, 11L, 21L);
    }

    @Test
    void completeSummaryUsesSingleFastReadWithoutCallingAi() {
        ShoeResponseDTO.ShoeDetailResultDTO completed =
                detail(21L, 88f, "이미 생성된 요약", "이미 생성된 리뷰 요약");
        when(queryService.getShoeDetail(7L, 11L, 21L)).thenReturn(completed);

        ShoeResponseDTO.ShoeDetailResultDTO result = service.getShoeDetail(7L, 11L, 21L);

        assertThat(result).isSameAs(completed);
        verifyNoInteractions(summaryGenerationTrigger);
    }

    @Test
    void generationFailureReturnsOriginalDetailInsteadOfBreakingRequest() {
        ShoeResponseDTO.ShoeDetailResultDTO pending = detail(21L, 88f, null, null);
        when(queryService.getShoeDetail(7L, 11L, 21L)).thenReturn(pending);
        when(summaryGenerationTrigger.generateNow(7L, 21L, 11L)).thenReturn(false);

        ShoeResponseDTO.ShoeDetailResultDTO result = service.getShoeDetail(7L, 11L, 21L);

        assertThat(result).isSameAs(pending);
        verify(queryService, never()).getShoeDetail(7L, 11L, null);
    }

    @Test
    void guestAndNoRecommendationPathsNeverCallAi() {
        ShoeResponseDTO.ShoeDetailResultDTO guest = detail(null, null, null, null);
        when(queryService.getShoeDetail(null, 11L, null)).thenReturn(guest);

        assertThat(service.getShoeDetail(null, 11L, null)).isSameAs(guest);

        verifyNoInteractions(summaryGenerationTrigger);
    }

    private static ShoeResponseDTO.ShoeDetailResultDTO detail(
            Long measurementSessionId,
            Float fitScore,
            String pointSummary,
            String reviewSummary) {
        List<ShoeResponseDTO.ReasonResultDTO> reasons = measurementSessionId == null
                ? List.of()
                : Arrays.stream(ReasonType.values())
                        .map(type -> ShoeResponseDTO.ReasonResultDTO.builder()
                                .reasonType(type)
                                .title(type.name())
                                .riskLevel(RiskLevel.LOW)
                                .reviewSummary(reviewSummary)
                                .reviewTexts(List.of())
                                .build())
                        .toList();
        return ShoeResponseDTO.ShoeDetailResultDTO.builder()
                .measurementSessionId(measurementSessionId)
                .id(11L)
                .fitScore(fitScore)
                .pointSummary(pointSummary)
                .reasons(reasons)
                .build();
    }
}
