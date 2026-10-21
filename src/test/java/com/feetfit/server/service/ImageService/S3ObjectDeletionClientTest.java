package com.feetfit.server.service.ImageService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3ObjectDeletionClientTest {
    private S3ObjectDeletionClient client;

    @BeforeEach
    void setup() {
        client = spy(new S3ObjectDeletionClient(new ObjectMapper()));
        ReflectionTestUtils.setField(client, "bucketName", "private-bucket");
        ReflectionTestUtils.setField(client, "region", "us-west-2");
        ReflectionTestUtils.setField(client, "publicUrlPrefix", "https://private-bucket.s3.us-west-2.amazonaws.com");
    }

    @Test
    void resolvesOnlyOwnedPrivateImages() {
        assertThat(client.resolveOwnedImage("https://private-bucket.s3.us-west-2.amazonaws.com/reports/photo.jpg"))
                .isEqualTo(new S3ObjectDeletionClient.ObjectKey("private-bucket", "reports/photo.jpg"));
        assertThat(client.resolveOwnedImage("s3://private-bucket/reports/photo.jpg")).isNotNull();
        for (String url : List.of("https://evil.example/reports/photo.jpg", "s3://other/reports/photo.jpg",
                "https://private-bucket.s3.us-west-2.amazonaws.com/drug-images/shared.jpg",
                "https://private-bucket.s3.us-west-2.amazonaws.com/reports/%2e%2e/photo.jpg",
                "https://private-bucket.s3.us-west-2.amazonaws.com.evil.example/reports/photo.jpg")) {
            assertThat(client.resolveOwnedImage(url)).isNull();
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void permanentlyDeletesVersionsAndMarkersButNotOtherKeysWithTheSamePrefix() {
        doAnswer(call -> {
            List<String> arguments = call.getArgument(0);
            return arguments.contains("list-object-versions") ? """
                    {"Versions":[{"Key":"reports/photo.jpg","VersionId":"v1"},
                    {"Key":"reports/photo.jpg-other","VersionId":"other"}],
                    "DeleteMarkers":[{"Key":"reports/photo.jpg","VersionId":"marker"}]}
                    """ : "{}";
        }).when(client).execute(anyList());

        client.delete("private-bucket", "reports/photo.jpg");

        ArgumentCaptor<List<String>> calls = ArgumentCaptor.forClass((Class) List.class);
        verify(client, times(4)).execute(calls.capture());
        assertThat(calls.getAllValues()).anySatisfy(arguments -> assertThat(arguments).contains("--version-id", "v1"));
        assertThat(calls.getAllValues()).anySatisfy(arguments -> assertThat(arguments).contains("--version-id", "marker"));
        assertThat(calls.getAllValues()).noneSatisfy(arguments -> assertThat(arguments).contains("other"));
    }

    @Test
    void forbiddenObjectsNeverExecuteAwsCommands() {
        assertThatThrownBy(() -> client.delete("other", "reports/photo.jpg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.delete("private-bucket", "drug-images/photo.jpg")).isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).execute(anyList());
    }
}
