package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// RunRepeat에서 수집한 신발 실측 정보
@Entity
@Table(name = "shoe_lab_measurement")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeLabMeasurement extends BaseEntity {

    // 신발 실측 정보 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shoe_lab_measurement_id", nullable = false)
    private Long id;

    // 실측 대상 신발 식별자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shoe_id", nullable = false)
    private Shoe shoe;

    // 실측 데이터 출처(현재 RUNREPEAT)
    @Column(nullable = false)
    @Builder.Default
    private String source = "RUNREPEAT";

    // RunRepeat에서 실측에 사용한 신발 사이즈
    @Column
    private String testedSize;

    // 신발 내부의 실제 길이(mm)
    @Column
    private Float internalLengthMm;

    // 신발 내부의 실제 너비 및 발볼 관련 실측값(mm)
    @Column
    private Float widthMm;

    // 신발 앞코(Toe Box) 부분의 실제 너비(mm)
    @Column
    private Float toeboxWidthMm;

    // 신발 앞코(Toe Box) 내부의 실제 높이(mm)
    @Column
    private Float toeboxHeightMm;

    // 신발 깔창(Insole)의 실제 두께(mm)
    @Column
    private Float insoleThicknessMm;

    // 뒤꿈치 부분의 지면부터 발이 놓이는 위치까지의 높이(mm)
    @Column
    private Float heelStackMm;

    // 전족부 부분의 지면부터 발이 놓이는 위치까지의 높이(mm)
    @Column
    private Float forefootStackMm;

    // RunRepeat 원본 데이터 페이지 URL
    @Column(nullable = false)
    private String sourceUrl;
}
