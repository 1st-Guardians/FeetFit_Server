package com.feetfit.server.repository;

import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);

    long countByDeviceId(Long deviceId);
}
