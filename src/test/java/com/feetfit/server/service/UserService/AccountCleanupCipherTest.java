package com.feetfit.server.service.UserService;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class AccountCleanupCipherTest {
    private final String key = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private final AccountCleanupCipher cipher = new AccountCleanupCipher(key);

    @Test
    void encryptedPayloadIsRandomizedAndDoesNotExposeCredentials() {
        String plaintext = "provider-secret-token and reports/private-photo.jpg";
        String first = cipher.encrypt(plaintext);
        assertThat(first).doesNotContain("provider-secret-token", "private-photo");
        assertThat(cipher.encrypt(plaintext)).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo(plaintext);
    }

    @Test
    void tamperingAndWrongKeysAreRejected() {
        String encrypted = cipher.encrypt("secret");
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(3));
        bytes[bytes.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt("v1." + Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalStateException.class);
        var other = new AccountCleanupCipher(Base64.getEncoder().encodeToString("abcdef0123456789abcdef0123456789".getBytes()));
        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void identityHashIsStableAndProviderSpecific() {
        assertThat(cipher.identityHash("KAKAO", "123")).hasSize(64)
                .isEqualTo(cipher.identityHash("KAKAO", "123"))
                .isNotEqualTo(cipher.identityHash("APPLE", "123"));
    }

    @Test
    void shortEncryptionKeysAreRejected() {
        assertThatThrownBy(() -> new AccountCleanupCipher(Base64.getEncoder().encodeToString("short".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
