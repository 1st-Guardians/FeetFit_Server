package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.enums.ShoeImportMatchStatus;
import com.feetfit.server.domain.enums.ShoeImportOperation;
import com.feetfit.server.domain.enums.ShoeImportSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ShoeIngestionResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportResult {
        private int requestedCount;
        private int processedCount;

        @Builder.Default
        private List<ImportItemResult> items = new ArrayList<>();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportItemResult {
        private String externalKey;
        private Long shoeId;
        private ShoeImportMatchStatus matchStatus;
        private ShoeImportOperation operation;

        @Builder.Default
        private List<Long> candidateShoeIds = new ArrayList<>();

        private Long auditId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditPageResult {
        private List<AuditItem> audits;
        private int currentPage;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;

        public static AuditPageResult from(Page<AuditItem> page) {
            return AuditPageResult.builder()
                    .audits(page.getContent())
                    .currentPage(page.getNumber())
                    .totalPages(page.getTotalPages())
                    .totalElements(page.getTotalElements())
                    .hasNext(page.hasNext())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditItem {
        private Long auditId;
        private ShoeImportSource source;
        private String externalKey;
        private String sourceUrl;
        private String sourceBrandName;
        private String sourceShoeName;
        private String sourceModelCode;
        private ShoeImportMatchStatus matchStatus;
        private Long matchedShoeId;
        private List<Long> candidateShoeIds;
        private String detail;
        private LocalDateTime capturedAt;
        private LocalDateTime createdAt;
    }
}
