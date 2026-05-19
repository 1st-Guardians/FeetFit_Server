package com.feetfit.server.repository;

import com.feetfit.server.domain.DeviceConnectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceConnectionLogRepository extends JpaRepository<DeviceConnectionLog, Long> {
}
