package com.feetfit.server.service.UserService;

import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class AccountCleanupCipher {
    private static final byte[] CONTEXT = "FeetFit/account-cleanup/v1".getBytes(StandardCharsets.UTF_8);
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AccountCleanupCipher(@Value("${account.cleanup.encryption-key:${jwt.secret}}") String encodedKey) {
        try {
            byte[] material = Decoders.BASE64.decode(encodedKey);
            if (material.length < 32) throw new IllegalArgumentException("Cleanup encryption key must contain at least 32 bytes.");
            Mac derivation = Mac.getInstance("HmacSHA256");
            derivation.init(new SecretKeySpec(material, "HmacSHA256"));
            key = new SecretKeySpec(derivation.doFinal(CONTEXT), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not initialize cleanup encryption.");
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(CONTEXT);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1." + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt cleanup payload.");
        }
    }

    public String decrypt(String value) {
        try {
            if (!value.startsWith("v1.")) throw new IllegalArgumentException();
            ByteBuffer bytes = ByteBuffer.wrap(Base64.getDecoder().decode(value.substring(3)));
            byte[] nonce = new byte[12];
            bytes.get(nonce);
            byte[] encrypted = new byte[bytes.remaining()];
            bytes.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(CONTEXT);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalStateException("Could not decrypt cleanup payload.");
        }
    }

    public String identityHash(String provider, String subject) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("identity\0" + provider + "\0" + subject)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not identify cleanup task.");
        }
    }
}
