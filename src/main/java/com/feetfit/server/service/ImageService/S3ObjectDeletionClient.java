package com.feetfit.server.service.ImageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class S3ObjectDeletionClient {
    public record ObjectKey(String bucket, String key) { }
    private static final Set<String> SHARED_FOLDERS = Set.of("drug-images", "shoe-images", "shoes");
    private final ObjectMapper mapper;

    @Value("${file.upload.bucket-name:project5-42-oregon-feetfit-s3}")
    private String bucketName;
    @Value("${file.upload.region:us-west-2}")
    private String region;
    @Value("${file.upload.public-url-prefix:https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com}")
    private String publicUrlPrefix;
    @Value("${file.upload.aws-cli-path:}")
    private String awsCliPath;

    public ObjectKey resolveOwnedImage(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI value = URI.create(url);
            URI base = URI.create(publicUrlPrefix);
            String key;
            if ("s3".equals(value.getScheme()) && bucketName.equals(value.getHost())) {
                key = value.getPath().substring(1);
            } else if (("https".equals(value.getScheme()) || "http".equals(value.getScheme()))
                    && base.getHost() != null && base.getHost().equals(value.getHost())
                    && base.getPort() == value.getPort() && value.getUserInfo() == null) {
                String prefix = base.getPath().replaceAll("/+$", "") + "/";
                if (!value.getPath().startsWith(prefix)) return null;
                key = value.getPath().substring(prefix.length());
            } else {
                return null;
            }
            return isPrivateKey(key) ? new ObjectKey(bucketName, key) : null;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    public void delete(String bucket, String key) {
        if (!bucketName.equals(bucket) || !isPrivateKey(key)) {
            throw new IllegalArgumentException("Object is outside the private upload namespace.");
        }
        execute(arguments("delete-object", bucket, "--key", key));
        JsonNode listing;
        try {
            listing = mapper.readTree(execute(arguments("list-object-versions", bucket, "--prefix", key)));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read S3 object versions.");
        }
        for (String field : List.of("Versions", "DeleteMarkers")) {
            for (JsonNode version : listing.path(field)) {
                if (key.equals(version.path("Key").asText()) && version.hasNonNull("VersionId")) {
                    execute(arguments("delete-object", bucket, "--key", key,
                            "--version-id", version.path("VersionId").asText()));
                }
            }
        }
    }

    public Set<String> referenceUrls(String key) {
        return Set.of(publicUrlPrefix.replaceAll("/+$", "") + "/" + key, "s3://" + bucketName + "/" + key);
    }

    private boolean isPrivateKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\\") || key.contains("\0")) return false;
        String[] parts = key.split("/", -1);
        if (parts.length < 2 || SHARED_FOLDERS.contains(parts[0])) return false;
        for (String part : parts) if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        return true;
    }

    private List<String> arguments(String operation, String bucket, String... options) {
        List<String> result = new ArrayList<>(List.of("s3api", operation, "--bucket", bucket));
        result.addAll(List.of(options));
        result.addAll(List.of("--region", region, "--output", "json", "--no-cli-pager"));
        return result;
    }

    String execute(List<String> arguments) {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("feetfit-s3-delete-", ".json");
            List<String> command = new ArrayList<>();
            command.add(resolveExecutable());
            command.addAll(arguments);
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
            builder.environment().put("AWS_PAGER", "");
            process = builder.start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IllegalStateException("S3 deletion timed out.");
            if (process.exitValue() != 0) throw new IllegalStateException("S3 deletion failed; check bucket permissions.");
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("S3 deletion interrupted.");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not execute S3 deletion.");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (output != null) try { Files.deleteIfExists(output); } catch (IOException ignored) { }
        }
    }

    private String resolveExecutable() {
        if (awsCliPath != null && !awsCliPath.isBlank()) return awsCliPath;
        for (String path : List.of("/usr/bin/aws", "/usr/local/bin/aws", "/opt/homebrew/bin/aws")) {
            if (Files.isExecutable(Path.of(path))) return path;
        }
        return "aws";
    }
}
