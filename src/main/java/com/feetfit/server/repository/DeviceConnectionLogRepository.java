package com.feetfit.server.repository;

import com.feetfit.server.domain.DeviceConnectionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeviceConnectionLogRepository extends JpaRepository<DeviceConnectionLog, Long> {
    @Query(value = """
            SELECT *
            FROM device_connection_logs log
            JOIN device d ON d.device_id = log.device_id
            WHERE d.user_id = :userId
              AND log.device_id = :deviceId
              AND connection_status = :connectionStatus
              AND connection_type IN ('BLUETOOTH', 'WIFI')
            ORDER BY started_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<DeviceConnectionLog> findLatestSupportedConnectionLog(
            @Param("userId") Long userId,
            @Param("deviceId") Long deviceId,
            @Param("connectionStatus") String connectionStatus
    );
}
