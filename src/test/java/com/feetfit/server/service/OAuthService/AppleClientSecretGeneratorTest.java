package com.feetfit.server.service.OAuthService;

import com.feetfit.server.config.AppleOAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AppleClientSecretGeneratorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void generate_readsP8AndCreatesEs256ClientSecret() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        Path privateKeyPath = tempDirectory.resolve("AuthKey_TEST.p8");
        String encodedKey = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(keyPair.getPrivate().getEncoded());
        Files.writeString(privateKeyPath,
                "-----BEGIN PRIVATE KEY-----\n" + encodedKey + "\n-----END PRIVATE KEY-----\n");

        AppleOAuthProperties properties = new AppleOAuthProperties();
        ReflectionTestUtils.setField(properties, "clientId", "test.bundle.id");
        ReflectionTestUtils.setField(properties, "teamId", "TEST_TEAM_ID");
        ReflectionTestUtils.setField(properties, "keyId", "TEST_KEY_ID");
        ReflectionTestUtils.setField(properties, "privateKeyPath", privateKeyPath.toString());
        ReflectionTestUtils.setField(properties, "privateKeyBase64", "");

        String clientSecret = new AppleClientSecretGenerator(properties).generate();
        Jws<Claims> parsed = Jwts.parserBuilder()
                .setSigningKey(keyPair.getPublic())
                .build()
                .parseClaimsJws(clientSecret);

        assertThat(parsed.getHeader().getKeyId()).isEqualTo("TEST_KEY_ID");
        assertThat(parsed.getBody().getIssuer()).isEqualTo("TEST_TEAM_ID");
        assertThat(parsed.getBody().getSubject()).isEqualTo("test.bundle.id");
        assertThat(parsed.getBody().getAudience()).isEqualTo("https://appleid.apple.com");
        assertThat(parsed.getBody().getExpiration()).isAfter(parsed.getBody().getIssuedAt());
    }

    @Test
    void generate_readsBase64P8AndCreatesEs256ClientSecret() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String encodedKey = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + encodedKey
                + "\n-----END PRIVATE KEY-----\n";

        AppleOAuthProperties properties = new AppleOAuthProperties();
        ReflectionTestUtils.setField(properties, "clientId", "test.bundle.id");
        ReflectionTestUtils.setField(properties, "teamId", "TEST_TEAM_ID");
        ReflectionTestUtils.setField(properties, "keyId", "TEST_KEY_ID");
        ReflectionTestUtils.setField(properties, "privateKeyPath", "");
        ReflectionTestUtils.setField(properties, "privateKeyBase64",
                Base64.getEncoder().encodeToString(pem.getBytes()));

        String clientSecret = new AppleClientSecretGenerator(properties).generate();
        Jws<Claims> parsed = Jwts.parserBuilder()
                .setSigningKey(keyPair.getPublic())
                .build()
                .parseClaimsJws(clientSecret);

        assertThat(parsed.getHeader().getKeyId()).isEqualTo("TEST_KEY_ID");
        assertThat(parsed.getBody().getIssuer()).isEqualTo("TEST_TEAM_ID");
        assertThat(parsed.getBody().getSubject()).isEqualTo("test.bundle.id");
    }
}
