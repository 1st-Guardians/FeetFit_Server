package com.feetfit.server.web.dto.drug;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class DrugResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugCategoryResultDTO {
        private List<DrugCategoryDTO> categories;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugCategoryDTO {
        private String id;
        private String label;
        private String eyebrow;
        private String description;
        private boolean online;
        private String accent;
        private String iconName;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugListResultDTO {
        private String category;
        private String categoryLabel;
        private String source;
        private List<DrugItemDTO> items;
        private int currentPage;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugItemDTO {
        private String id;
        private String name;
        private String company;
        private String image;
        private String efficacy;
        private String usage;
        private String warning;
        private String interaction;
        private String sideEffect;
        private String storage;
        private String category;
        private Boolean online;
        private String priceHint;
        private String detailUrl;
        private String description;
        private List<String> checklist;
        private PermitInfoDTO permit;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugPermitResultDTO {
        private DrugItemDTO drug;
        private PermitInfoDTO permit;
        private RecallInfoDTO recall;
        private String source;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermitInfoDTO {
        private String itemSeq;
        private String englishName;
        private String classification;
        private String status;
        private boolean cancelled;
        private String permitDate;
        private String permitType;
        private String appearance;
        private String mainIngredient;
        private String packUnit;
        private String storage;
        private String validTerm;
        private String atcCode;
        private String officialUrl;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecallInfoDTO {
        private String status;
        private List<RecallRecordDTO> records;
        private String checkedAt;
        private String sourceUrl;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecallRecordDTO {
        private String product;
        private String company;
        private String reason;
        private boolean mandatory;
        private String approvalDate;
        private String commandDate;
    }
}
