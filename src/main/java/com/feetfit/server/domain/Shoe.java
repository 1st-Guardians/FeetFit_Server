package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shoe")
@Getter
@DynamicUpdate
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Shoe extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brandName;

    @Column(nullable = false)
    private String shoeName;

    @Column(nullable = false)
    private String modelCode;

    @Column(name = "musinsa_goods_no", unique = true)
    private String musinsaGoodsNo;

    @Column(nullable = false)
    private String musinsaUrl;

    @Column
    private Integer price;

    @Column
    private String imageUrl;

    @Column
    private Float overallRating;

    @Column(nullable = false)
    @Builder.Default
    private Integer clickCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @OneToMany(mappedBy = "shoe", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ShoeRecommendation> recommendations = new ArrayList<>();

    @OneToMany(mappedBy = "shoe", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ShoeReview> reviews = new ArrayList<>();

    @OneToMany(mappedBy = "shoe", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ShoeLabMeasurement> labMeasurements = new ArrayList<>();

    public void updateCrawlerData(
            String brandName,
            String shoeName,
            String modelCode,
            String musinsaGoodsNo,
            String musinsaUrl,
            Integer price,
            String imageUrl,
            Float overallRating,
            Integer reviewCount) {
        this.brandName = brandName;
        this.shoeName = shoeName;
        this.modelCode = modelCode;
        this.musinsaGoodsNo = musinsaGoodsNo;
        this.musinsaUrl = musinsaUrl;
        if (price != null) {
            this.price = price;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (overallRating != null) {
            this.overallRating = overallRating;
        }
        if (reviewCount != null) {
            this.reviewCount = reviewCount;
        }
    }
}
