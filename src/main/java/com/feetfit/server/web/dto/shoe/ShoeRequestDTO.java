package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import com.feetfit.server.domain.enums.ShoeSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    }

    @Getter
    @NoArgsConstructor
    @Schema(
            description = "사용자-신발 발 적합도 생성/갱신 요청 (Feetfit_AI가 신발 전체에 대해 배치로 호출). "
                    + "reasons는 FOREFOOT/HEEL/INSOLE을 한 번에 배열로 담아 보낼 수 있으며, "
                    + "갱신 시 기존 reasons를 통째로 교체하므로 3개 모두 유지하려면 매번 3개를 모두 보내야 합니다. "
                    + "pointSummary·reviewSummary는 신발 상세 조회 시 AI가 별도로 생성하므로 배치에서는 전송하지 않습니다.",
            example = """
                    {
                      "measurementSessionId": 28,
                      "recommendations": [
                        {
                          "shoeId": 45,
                          "fitScore": 87,
                          "reasons": [
                            {
                              "reasonType": "FOREFOOT",
                              "title": "발볼 여유로움",
                              "riskLevel": "LOW",
                              "reviewIds": [3033, 3044, 3055]
                            },
                            {
                              "reasonType": "HEEL",
                              "title": "뒤꿈치 까짐 주의",
                              "riskLevel": "MEDIUM",
                              "reviewIds": [3021, 3040]
                            },
                            {
                              "reasonType": "INSOLE",
                              "title": "쿠션/통기성 우수",
                              "riskLevel": "LOW",
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
        private Long measurementSessionId;

        @Schema(description = "신발별 발 적합도 목록")
        @NotNull(message = "recommendations는 필수입니다.")
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
        private Float fitScore;

        @Schema(description = "부위별(발볼/뒤꿈치/깔창) 근거 목록. FOREFOOT/HEEL/INSOLE을 배열에 함께 담아 한 번에 보낼 수 있습니다. "
                + "최소 1개 이상이어야 하며, 갱신 시 기존 근거를 통째로 교체하므로 3개를 모두 유지하려면 매번 3개를 모두 보내야 합니다.")
        @NotEmpty(message = "reasons는 최소 1개 이상이어야 합니다.")
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

        @Schema(description = "근거로 사용된 리뷰 ID 목록", example = "[101, 205, 300]")
        @NotNull(message = "reviewIds는 필수입니다.")
        private List<Long> reviewIds;
    }
}