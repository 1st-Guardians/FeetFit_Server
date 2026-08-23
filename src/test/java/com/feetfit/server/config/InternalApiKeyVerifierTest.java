package com.feetfit.server.config;

import com.feetfit.server.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalApiKeyVerifierTest {

    @Test
    void failsClosedWhenServerKeyIsNotConfigured() {
        InternalApiKeyVerifier verifier = new InternalApiKeyVerifier("");

        assertThatThrownBy(() -> verifier.verify("caller-key"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void acceptsOnlyExactConfiguredKey() {
        InternalApiKeyVerifier verifier = new InternalApiKeyVerifier("service-secret");

        assertThatCode(() -> verifier.verify("service-secret")).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verify("wrong-secret"))
                .isInstanceOf(GeneralException.class);
    }
}
