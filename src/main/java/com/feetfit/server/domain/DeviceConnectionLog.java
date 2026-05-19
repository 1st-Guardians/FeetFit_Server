package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ConnectionStatus;
import com.feetfit.server.domain.enums.ConnectionType;
import com.feetfit.server.domain.enums.DeviceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_connection_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DeviceConnectionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionType connectionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus connectionStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status;

    @Column
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime endedAt;

    public static DeviceConnectionLog connected(User user, Device device, LocalDateTime occurredAt) {
        return DeviceConnectionLog.builder()
                .device(device)
                .user(user)
                .connectionType(ConnectionType.APP)
                .connectionStatus(ConnectionStatus.CONNECTED)
                .status(device.getStatus())
                .startedAt(occurredAt)
                .endedAt(occurredAt)
                .build();
    }

    public static DeviceConnectionLog disconnected(User user, Device device, LocalDateTime occurredAt) {
        return DeviceConnectionLog.builder()
                .device(device)
                .user(user)
                .connectionType(ConnectionType.APP)
                .connectionStatus(ConnectionStatus.DISCONNECTED)
                .status(device.getStatus())
                .startedAt(occurredAt)
                .endedAt(occurredAt)
                .build();
    }
}
