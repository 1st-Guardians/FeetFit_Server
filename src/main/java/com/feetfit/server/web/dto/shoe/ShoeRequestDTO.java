package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import com.feetfit.server.domain.enums.ShoeSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class ShoeRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeListRequestDTO {
        private ShoeSort sort = ShoeSort.FIT_SCORE;
        private int page = 0;
        private int size = 20;
        private Long measurementSessionId;
    }

    @Getter
    @NoArgsConstructor
    @Schema(
            description = "사용자-신발 발 적합도 생성/갱신 요청 (Feetfit_AI가 신발 전체에 대해 배치로 호출). "
                    + "reasons는 FOREFOOT/HEEL/INSOLE을 한 번에 배열로 담아 보낼 수 있으며, "
                    + "갱신 시 기존 reasons를 통째로 교체하므로 3개 모두 유지하려면 매번 3개를 모두 보내야 합니다.",
            example = """
                    {
                      "measurementSessionId": 28,
                      "recommendations": [
                        {
                          "shoeId": 45,
                          "fitScore": 87,
                          "pointSummary": "발볼이 슬림하게 나온 편이라...",
                          "reasons": [
                            {
                              "reasonType": "FOREFOOT",
                              "title": "발볼 여유로움",
                              "riskLevel": "LOW",
                              "reviewSummary": "발볼과 앞코가 여유 있다는 리뷰가 많습니다.",
                              "reviewIds": [3033, 3044, 3055]
                            },
                            {
                              "reasonType": "HEEL",
                              "title": "뒤꿈치 까짐 주의",
                              "riskLevel": "MEDIUM",
                              "reviewSummary": "뒤꿈치 마찰에 대한 의견이 엇갈립니다.",
                              "reviewIds": [3021, 3040]
                            },
                            {
                              "reasonType": "INSOLE",
                              "title": "쿠션/통기성 우수",
                              "riskLevel": "LOW",
                              "reviewSummary": "쿠션감과 통기성이 좋다는 의견이 많습니다.",
                              "reviewIds": [3012, 3018, 3050]
                            }
                          ]
                        }
                      ]
                    }
                    """
    )
    public static class SaveShoeRecommendationDTO {

        @Schema(description = "측정 세션 ID", example = "12")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        @Positive(message = "측정 세션 ID는 양수여야 합니다.")
        private Long measurementSessionId;

        @Schema(description = "신발별 발 적합도 목록")
        @NotEmpty(message = "recommendations는 비어 있을 수 없습니다.")
        @Valid
        private List<ShoeRecommendationItemDTO> recommendations;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "신발 하나에 대한 발 적합도")
    public static class ShoeRecommendationItemDTO {

        @Schema(description = "신발 ID", example = "5")
        @NotNull(message = "신발 ID는 필수입니다.")
        private Long shoeId;

        @Schema(description = "종합 발 적합도 점수 (0~100)", example = "87.0")
        @NotNull(message = "발 적합도 점수는 필수입니다.")
        @DecimalMin(value = "0.0", message = "fitScore는 0 이상이어야 합니다.")
        @DecimalMax(value = "100.0", message = "fitScore는 100 이하여야 합니다.")
        private Float fitScore;

        @Schema(description = "착용 포인트 종합 설명", example = "발볼이 슬림하게 나온 편이라...")
        private String pointSummary;

        @Schema(description = "부위별(발볼/뒤꿈치/깔창) 근거 목록. FOREFOOT/HEEL/INSOLE을 배열에 함께 담아 한 번에 보낼 수 있습니다. "
                + "최소 1개 이상이어야 하며, 갱신 시 기존 근거를 통째로 교체하므로 3개를 모두 유지하려면 매번 3개를 모두 보내야 합니다.")
        @NotEmpty(message = "reasons는 최소 1개 이상이어야 합니다.")
        @Size(min = 3, max = 3, message = "reasons는 FOREFOOT/HEEL/INSOLE 3개여야 합니다.")
        @Valid
        private List<ShoeRecommendationReasonDTO> reasons;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "부위별 발 적합도 근거")
    public static class ShoeRecommendationReasonDTO {

        @Schema(description = "부위 타입", example = "FOREFOOT", allowableValues = {"FOREFOOT", "HEEL", "INSOLE"})
        @NotNull(message = "reasonType은 필수입니다.")
        private ReasonType reasonType;

        @Schema(description = "짧은 제목", example = "발볼 여유로움")
        @NotBlank(message = "title은 필수입니다.")
        private String title;

        @Schema(description = "해당 부위 위험도", example = "LOW", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        @NotNull(message = "riskLevel은 필수입니다.")
        private RiskLevel riskLevel;

        @Schema(description = "리뷰 기반 요약 (2줄 내외)")
        private String reviewSummary;

        @Schema(description = "근거로 사용된 리뷰 ID 목록", example = "[101, 205, 300]")
        @NotNull(message = "reviewIds는 필수입니다.")
        @Size(max = 3, message = "각 reason에는 리뷰를 최대 3개까지 연결할 수 있습니다.")
        private List<@NotNull Long> reviewIds;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "AI가 생성한 추천 설명 저장 요청")
    public static class SaveShoeSummariesDTO {

        @NotNull(message = "측정 세션 ID는 필수입니다.")
        @Positive(message = "측정 세션 ID는 양수여야 합니다.")
        private Long measurementSessionId;

        @NotBlank(message = "pointSummary는 필수입니다.")
        private String pointSummary;

        @NotEmpty(message = "reasons는 필수입니다.")
        @Size(min = 3, max = 3, message = "reasons는 FOREFOOT/HEEL/INSOLE 3개여야 합니다.")
        @Valid
        private List<ShoeReasonSummaryDTO> reasons;
    }

    @Getter
    @NoArgsConstructor
    public static class ShoeReasonSummaryDTO {

        @NotNull(message = "reasonType은 필수입니다.")
        private ReasonType reasonType;

        @NotBlank(message = "reviewSummary는 필수입니다.")
        private String reviewSummary;
    }
}
