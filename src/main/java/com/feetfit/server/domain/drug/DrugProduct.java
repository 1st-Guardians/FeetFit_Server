package com.feetfit.server.domain.drug;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.DrugCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "drug_product")
@Getter
@DynamicUpdate
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DrugProduct extends BaseEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(length = 500)
    private String company;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String categoryLabel;

    @Column(nullable = false)
    private boolean online;

    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean recommendationEligible;

    @Column(nullable = false)
    private int displayOrder;

    @ElementCollection
    @CollectionTable(name = "drug_product_category",
            joinColumns = @JoinColumn(name = "drug_product_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"drug_product_id", "category"}),
            indexes = @Index(name = "idx_drug_product_category", columnList = "category"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    @Builder.Default
    private Set<DrugCategory> categories = new HashSet<>();

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String itemPayload;

    @Column(columnDefinition = "LONGTEXT")
    private String permitPayload;

    @Column(columnDefinition = "LONGTEXT")
    private String recallPayload;

    @Column(nullable = false)
    private Instant fetchedAt;

    public void updateProduct(DrugCategory category, String name, String company, String description,
                              String categoryLabel, boolean online, int displayOrder, String itemPayload) {
        this.categories.add(category);
        this.name = name;
        this.company = company;
        this.description = description;
        this.categoryLabel = categoryLabel;
        this.online = online;
        if (online) {
            this.recommendationEligible = true;
        }
        this.displayOrder = displayOrder;
        this.itemPayload = itemPayload;
        this.fetchedAt = Instant.now();
    }

    public void updatePermit(String permitPayload) {
        this.permitPayload = permitPayload;
    }

    public void updateRecall(String recallPayload) {
        this.recallPayload = recallPayload;
    }

    public void updateRecommendationEligibility(boolean eligible) {
        this.recommendationEligible = eligible;
    }
}
