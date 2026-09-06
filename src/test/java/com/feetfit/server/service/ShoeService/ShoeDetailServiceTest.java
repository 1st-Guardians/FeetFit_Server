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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    void batchGeneratedCompleteSummaryUsesOneDbReadWithoutCallingDetailAi() {
        ShoeResponseDTO.ShoeDetailResultDTO completed =
                detail(
                        21L,
                        88f,
                        "발볼이 넓다면 반 사이즈 업을 고려할 수 있고, 정사이즈는 안정적인 핏입니다.",
                        "사용자의 발 압력과 제품 특성을 함께 보면 장시간 착용 시 주의가 필요합니다.");
        when(queryService.getShoeDetail(7L, 11L, 21L)).thenReturn(completed);

        ShoeResponseDTO.ShoeDetailResultDTO result = service.getShoeDetail(7L, 11L, 21L);

        assertThat(result).isSameAs(completed);
        verifyNoInteractions(summaryGenerationTrigger);
        verify(queryService, times(1)).getShoeDetail(7L, 11L, 21L);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    void legacyReviewTemplateIsRegeneratedOnceAndNaturalResultIsNotRegeneratedAgain() {
        ShoeResponseDTO.ShoeDetailResultDTO legacy = detail(
                21L,
                88f,
                "발볼은 비교적 여유 있게 나온 편이라 정사이즈 선택이 잘 맞습니다.",
                "발볼 적합도 주의. 정량 분석 기준 위험도는 주의입니다. "
                        + "관련 착화 리뷰 3개를 함께 확인해 주세요.");
        ShoeResponseDTO.ShoeDetailResultDTO natural = detail(
                21L,
                88f,
                "발볼이 넓다면 반 사이즈 업을 고려할 수 있고, 정사이즈는 안정적인 핏입니다.",
                "전족부 하중과 신발의 발볼 공간을 함께 보면 사이즈 선택에 주의가 필요합니다.");
        when(queryService.getShoeDetail(7L, 11L, 21L))
                .thenReturn(legacy, natural, natural);
        when(summaryGenerationTrigger.generateNow(7L, 21L, 11L)).thenReturn(true);

        ShoeResponseDTO.ShoeDetailResultDTO first = service.getShoeDetail(7L, 11L, 21L);
        ShoeResponseDTO.ShoeDetailResultDTO second = service.getShoeDetail(7L, 11L, 21L);

        assertThat(first).isSameAs(natural);
        assertThat(second).isSameAs(natural);
        verify(summaryGenerationTrigger, times(1)).generateNow(7L, 21L, 11L);
        verify(queryService, times(3)).getShoeDetail(7L, 11L, 21L);
    }

    @Test
    void pointSummaryContainingOnlyLegacyMeasurementLevelsIsRegenerated() {
        ShoeResponseDTO.ShoeDetailResultDTO legacy = detail(
                21L,
                88f,
                "발볼 공간은 낮은 편입니다. 앞코 공간은 낮은 편입니다. "
                        + "착화 시 가볍게 느껴지는 편입니다. 쿠션감은 낮은 편입니다.",
                "사용자의 발 상태와 제품 특성을 함께 설명한 자연스러운 요약입니다.");
        ShoeResponseDTO.ShoeDetailResultDTO natural = detail(
                21L,
                88f,
                "발볼이 슬림한 제품이라 발볼이 넓다면 반 사이즈 업을 고려해 주세요.",
                "사용자의 발 상태와 제품 특성을 함께 설명한 자연스러운 요약입니다.");
        when(queryService.getShoeDetail(7L, 11L, 21L)).thenReturn(legacy, natural);
        when(summaryGenerationTrigger.generateNow(7L, 21L, 11L)).thenReturn(true);

        assertThat(service.getShoeDetail(7L, 11L, 21L)).isSameAs(natural);

        verify(summaryGenerationTrigger).generateNow(7L, 21L, 11L);
        verify(queryService, times(2)).getShoeDetail(7L, 11L, 21L);
    }

    @Test
    void naturalRecommendationIsCompleteEvenWhenItMentionsOneMeasurementLevel() {
        ShoeResponseDTO.ShoeDetailResultDTO natural = detail(
                21L,
                88f,
                "발볼 공간은 낮은 편입니다. 발볼이 넓다면 반 사이즈 업을 고려해 주세요.",
                "전족부 하중과 신발의 발볼 공간을 함께 보면 사이즈 선택에 주의가 필요합니다.");
        when(queryService.getShoeDetail(7L, 11L, 21L)).thenReturn(natural);

        assertThat(service.getShoeDetail(7L, 11L, 21L)).isSameAs(natural);

        verifyNoInteractions(summaryGenerationTrigger);
        verify(queryService, times(1)).getShoeDetail(7L, 11L, 21L);
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
