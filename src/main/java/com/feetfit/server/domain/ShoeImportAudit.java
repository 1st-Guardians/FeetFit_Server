package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ShoeImportMatchStatus;
import com.feetfit.server.domain.enums.ShoeImportSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "shoe_import_audit",
        indexes = {
                @Index(name = "idx_shoe_import_audit_source_status", columnList = "source,match_status"),
                @Index(name = "idx_shoe_import_audit_external_key", columnList = "external_key")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeImportAudit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shoe_import_audit_id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShoeImportSource source;

    @Column(name = "external_key")
    private String externalKey;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "source_brand_name")
    private String sourceBrandName;

    @Column(name = "source_shoe_name")
    private String sourceShoeName;

    @Column(name = "source_model_code")
    private String sourceModelCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private ShoeImportMatchStatus matchStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_shoe_id")
    private Shoe matchedShoe;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_shoe_ids", nullable = false, columnDefinition = "JSON")
    @Builder.Default
    private List<Long> candidateShoeIds = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "raw_payload", columnDefinition = "LONGTEXT")
    private String rawPayload;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;
}
