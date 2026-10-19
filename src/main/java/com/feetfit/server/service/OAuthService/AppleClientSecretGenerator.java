package com.feetfit.server.service.OAuthService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.AuthHandler;
import com.feetfit.server.config.AppleOAuthProperties;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class AppleClientSecretGenerator {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final long CLIENT_SECRET_VALIDITY_SECONDS = 300L;

    private final AppleOAuthProperties properties;

    public String generate() {
        properties.validate();
        try {
            Instant issuedAt = Instant.now();
            return Jwts.builder()
                    .setHeaderParam(JwsHeader.KEY_ID, properties.getKeyId())
                    .setIssuer(properties.getTeamId())
                    .setAudience(APPLE_ISSUER)
                    .setSubject(properties.getClientId())
                    .setIssuedAt(Date.from(issuedAt))
                    .setExpiration(Date.from(issuedAt.plusSeconds(CLIENT_SECRET_VALIDITY_SECONDS)))
                    .signWith(loadPrivateKey(), SignatureAlgorithm.ES256)
                    .compact();
        } catch (AuthHandler exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthHandler(ErrorStatus.APPLE_CLIENT_SECRET_GENERATION_FAILED);
        }
    }

    private PrivateKey loadPrivateKey() {
        try {
            String pem = readPrivateKeyPem()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("EC").generatePrivate(keySpec);
        } catch (Exception exception) {
            throw new AuthHandler(ErrorStatus.APPLE_PRIVATE_KEY_INVALID);
        }
    }

    private String readPrivateKeyPem() throws Exception {
        if (StringUtils.hasText(properties.getPrivateKeyBase64())) {
            byte[] decoded = Base64.getDecoder().decode(properties.getPrivateKeyBase64());
            return new String(decoded, StandardCharsets.UTF_8);
        }

        try (InputStream inputStream = resolvePrivateKeyResource().getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Resource resolvePrivateKeyResource() {
        String path = properties.getPrivateKeyPath();
        if (path.startsWith("classpath:")) {
            return new ClassPathResource(path.substring("classpath:".length()));
        }
        if (path.startsWith("file:")) {
            return new FileSystemResource(path.substring("file:".length()));
        }
        return new FileSystemResource(path);
    }
}
