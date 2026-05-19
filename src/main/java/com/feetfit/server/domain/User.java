package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ConnectionType;
import com.feetfit.server.domain.enums.Gender;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@DynamicInsert
@DynamicUpdate
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column
    private Integer age;

    @Column
    private Float heightCm;

    @Column
    private Float weightKg;

    @Column
    private Integer footSize;

    @Enumerated(EnumType.STRING)
    @Column
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialType socialType;

    @Column(unique = true, nullable = false)
    private String socialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<UserTerms> termsList = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column
    private ConnectionType deviceConnectionType;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<MeasurementSession> measurementSessions = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Report> reports = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ShoeRecommendation> shoeRecommendations = new ArrayList<>();

    public void updateProfile(
            String nickname,
            Integer age,
            Float heightCm,
            Float weightKg,
            Integer footSize,
            Gender gender
    ) {
        this.nickname = nickname;
        this.age = age;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.footSize = footSize;
        this.gender = gender;
    }

    public void connectDevice(Device device, ConnectionType connectionType) {
        this.device = device;
        this.deviceConnectionType = connectionType;
    }

    public void disconnectDevice() {
        this.device = null;
        this.deviceConnectionType = null;
    }
}
