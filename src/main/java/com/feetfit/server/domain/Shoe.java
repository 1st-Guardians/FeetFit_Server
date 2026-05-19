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
    private String shoeUrl;

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

    public void incrementClickCount() {
        this.clickCount++;
    }
}
