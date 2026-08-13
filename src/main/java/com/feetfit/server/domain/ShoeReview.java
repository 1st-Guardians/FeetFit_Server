package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shoe_review")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shoe_id", nullable = false)
    private Shoe shoe;

    @Column(nullable = false)
    private Float rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reviewText;

    // 리뷰 수집 출처
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShoeReviewSource source = ShoeReviewSource.MUSINSA;

    @Column(nullable = false)
    private LocalDateTime collectedAt;
}
