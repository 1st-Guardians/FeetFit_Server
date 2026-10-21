package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_cleanup_task", indexes = {
        @Index(name = "idx_account_cleanup_due", columnList = "next_attempt_at"),
        @Index(name = "idx_account_cleanup_identity", columnList = "identity_hash")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountCleanupTask extends BaseEntity {
    public enum Kind { S3_IMAGE, KAKAO_UNLINK, APPLE_REVOKE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedPayload;

    @Column(length = 64)
    private String identityHash;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    private LocalDateTime leaseUntil;

    @Column(length = 36)
    private String claimToken;

    public AccountCleanupTask(Kind kind, String encryptedPayload, String identityHash) {
        this.kind = kind;
        this.encryptedPayload = encryptedPayload;
        this.identityHash = identityHash;
        this.nextAttemptAt = LocalDateTime.now();
    }

    public boolean claim(LocalDateTime now, String token) {
        if (nextAttemptAt.isAfter(now) || (leaseUntil != null && leaseUntil.isAfter(now))) return false;
        claimToken = token;
        leaseUntil = now.plusMinutes(5);
        attempts++;
        return true;
    }

    public void retry(String token) {
        if (!token.equals(claimToken)) return;
        nextAttemptAt = LocalDateTime.now().plusSeconds(Math.min(3600L, 5L << Math.min(attempts, 10)));
        leaseUntil = null;
        claimToken = null;
    }
}
