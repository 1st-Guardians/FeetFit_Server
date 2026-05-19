package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ConnectionStatus;
import com.feetfit.server.domain.enums.DeviceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "device")
@Getter
@DynamicInsert
@DynamicUpdate
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Device extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.AVAILABLE;

    @OneToMany(mappedBy = "device")
    @Builder.Default
    private List<User> users = new ArrayList<>();

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL)
    @Builder.Default
    private List<DeviceConnectionLog> connectionLogs = new ArrayList<>();

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL)
    @Builder.Default
    private List<MeasurementSession> measurementSessions = new ArrayList<>();

    public void markConnected() {
        this.connectionStatus = ConnectionStatus.CONNECTED;
        this.status = DeviceStatus.REGISTERED;
    }

    public void markAvailable() {
        this.connectionStatus = ConnectionStatus.DISCONNECTED;
        this.status = DeviceStatus.AVAILABLE;
    }
}
