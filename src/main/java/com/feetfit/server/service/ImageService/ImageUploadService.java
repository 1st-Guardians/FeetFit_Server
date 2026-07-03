package com.feetfit.server.service.ImageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final String DEFAULT_CLI_PATH = String.join(":",
            "/usr/local/sbin",
            "/usr/local/bin",
            "/usr/sbin",
            "/usr/bin",
            "/sbin",
            "/bin",
            "/usr/local/aws-cli/v2/current/bin",
            "/root/.local/bin",
            "/home/ubuntu/.local/bin",
            "/opt/homebrew/bin"
    );

    @Value("${file.upload.bucket-name:project5-42-oregon-feetfit-s3}")
    private String bucketName;

    @Value("${file.upload.region:us-west-2}")
    private String region;

    @Value("${file.upload.public-url-prefix:https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com}")
    private String publicUrlPrefix;

    @Value("${file.upload.aws-cli-path:}")
    private String awsCliPath;

    @Value("${file.upload.storage-mode:AUTO}")
    private String storageMode;

    @Value("${file.upload.proxy-upload-url:}")
    private String proxyUploadUrl;

    @Value("${swagger.deploy-server-url:}")
    private String deployServerUrl;

    private final ObjectMapper objectMapper;

    public ImageResponseDTO.UploadImageResultDTO upload(String folderName, MultipartFile image) {
        validateImage(image);

        String sanitizedFolderName = sanitizeFolderName(folderName);
        if (shouldProxyUpload()) {
            return uploadViaDeployServer(sanitizedFolderName, image);
        }

        validateUploadConfig();

        String originalFileName = image.getOriginalFilename();
        String extension = extractExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + "." + extension;
        String s3Key = sanitizedFolderName + "/" + storedFileName;
        String s3Uri = "s3://" + bucketName + "/" + s3Key;

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("feetfit-upload-", "." + extension);
            image.transferTo(tempFile);
            uploadToS3(tempFile, s3Uri, image.getContentType());
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "이미지 임시 파일 처리에 실패했습니다.");
        } finally {
            deleteTempFile(tempFile);
        }

        String imageUrl = normalizePublicUrlPrefix(publicUrlPrefix) + "/" + s3Key;

        return ImageResponseDTO.UploadImageResultDTO.builder()
                .folderName(sanitizedFolderName)
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .contentType(image.getContentType())
                .size(image.getSize())
                .bucketName(bucketName)
                .s3Key(s3Key)
                .s3Uri(s3Uri)
                .imageUrl(imageUrl)
                .build();
    }

    private boolean shouldProxyUpload() {
        String mode = storageMode == null ? "AUTO" : storageMode.trim().toUpperCase();
        if ("PROXY".equals(mode)) {
            return true;
        }
        if ("S3".equals(mode)) {
            return false;
        }
        return !isAwsCliAvailable();
    }

    private ImageResponseDTO.UploadImageResultDTO uploadViaDeployServer(String folderName, MultipartFile image) {
        String uploadUrl = resolveProxyUploadUrl();
        String authorization = resolveAuthorizationHeader();

        try {
            ByteArrayResource imageResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename();
                }
            };

            MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
            multipartBodyBuilder.part("folderName", folderName);
            multipartBodyBuilder.part("image", imageResource)
                    .filename(image.getOriginalFilename())
                    .contentType(MediaType.parseMediaType(image.getContentType()));

            String responseBody = WebClient.builder()
                    .build()
                    .post()
                    .uri(uploadUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> {
                        if (authorization != null && !authorization.isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, authorization);
                        }
                    })
                    .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseUploadResponse(responseBody);
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "배포 서버로 전달할 이미지 파일 처리에 실패했습니다.");
        } catch (WebClientException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "배포 서버 이미지 업로드 요청에 실패했습니다. " + e.getMessage());
        }
    }

    private String resolveProxyUploadUrl() {
        if (proxyUploadUrl != null && !proxyUploadUrl.isBlank()) {
            return proxyUploadUrl;
        }
        if (deployServerUrl == null || deployServerUrl.isBlank()) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "배포 서버 이미지 업로드 URL 설정이 필요합니다.");
        }
        return normalizePublicUrlPrefix(deployServerUrl) + "/api/images/upload";
    }

    private String resolveAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }

    private ImageResponseDTO.UploadImageResultDTO parseUploadResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.path("isSuccess").asBoolean(false)) {
                throw new GeneralException(
                        ErrorStatus._INTERNAL_SERVER_ERROR,
                        "배포 서버 이미지 업로드에 실패했습니다. " + root.path("message").asText()
                );
            }

            JsonNode result = root.path("result");
            if (result.isMissingNode() || result.isNull()) {
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "배포 서버 이미지 업로드 응답이 비어 있습니다.");
            }

            return ImageResponseDTO.UploadImageResultDTO.builder()
                    .folderName(textOrNull(result, "folderName"))
                    .originalFileName(textOrNull(result, "originalFileName"))
                    .storedFileName(textOrNull(result, "storedFileName"))
                    .contentType(textOrNull(result, "contentType"))
                    .size(result.path("size").isNumber() ? result.path("size").asLong() : null)
                    .bucketName(textOrNull(result, "bucketName"))
                    .s3Key(textOrNull(result, "s3Key"))
                    .s3Uri(textOrNull(result, "s3Uri"))
                    .imageUrl(textOrNull(result, "imageUrl"))
                    .build();
        } catch (GeneralException e) {
            throw e;
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "배포 서버 이미지 업로드 응답을 읽을 수 없습니다.");
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private void validateUploadConfig() {
        if (bucketName == null || bucketName.isBlank()) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "S3 버킷 설정이 필요합니다.");
        }

        if (region == null || region.isBlank()) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "S3 리전 설정이 필요합니다.");
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "업로드할 이미지 파일은 필수입니다.");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다.");
        }

        String extension = extractExtension(image.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "jpg, jpeg, png, webp, gif 파일만 업로드할 수 있습니다.");
        }
    }

    private String sanitizeFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "폴더 이름은 필수입니다.");
        }

        String sanitized = folderName.trim();
        if (!sanitized.matches("^[a-zA-Z0-9_-]{1,50}$")) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "폴더 이름은 영문, 숫자, -, _만 사용할 수 있습니다.");
        }
        return sanitized;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "파일 확장자가 필요합니다.");
        }

        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private void uploadToS3(Path tempFile, String s3Uri, String contentType) {
        String awsCommand = resolveAwsCliCommand();
        String uploadCommand = String.join(" ",
                shellQuote(awsCommand),
                "s3",
                "cp",
                shellQuote(tempFile.toString()),
                shellQuote(s3Uri),
                "--region",
                shellQuote(region),
                "--content-type",
                shellQuote(contentType == null ? "application/octet-stream" : contentType)
        );

        ProcessBuilder processBuilder = new ProcessBuilder(
                "/bin/sh",
                "-c",
                uploadCommand
        );
        processBuilder.environment().put("PATH", DEFAULT_CLI_PATH);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "S3 이미지 업로드에 실패했습니다. " + output);
            }
        } catch (IOException e) {
            throw new GeneralException(
                    ErrorStatus._INTERNAL_SERVER_ERROR,
                    "AWS CLI를 실행할 수 없습니다. awsCliPath=" + awsCommand
                            + ", processPath=" + DEFAULT_CLI_PATH
                            + ", javaPath=" + System.getenv("PATH")
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "S3 이미지 업로드가 중단되었습니다.");
        }
    }

    private String resolveAwsCliCommand() {
        if (awsCliPath != null && !awsCliPath.isBlank()) {
            return awsCliPath;
        }

        for (String candidate : new String[]{"/usr/bin/aws", "/usr/local/bin/aws", "/opt/homebrew/bin/aws"}) {
            Path path = Path.of(candidate);
            if (Files.isExecutable(path)) {
                return candidate;
            }
        }

        return "aws";
    }

    private boolean isAwsCliAvailable() {
        String awsCommand = resolveAwsCliCommand();
        if (!"aws".equals(awsCommand)) {
            return Files.isExecutable(Path.of(awsCommand));
        }

        for (String candidate : new String[]{"/usr/bin/aws", "/usr/local/bin/aws", "/opt/homebrew/bin/aws"}) {
            if (Files.isExecutable(Path.of(candidate))) {
                return true;
            }
        }

        return false;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String readProcessOutput(Process process) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        process.getInputStream().transferTo(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8).trim();
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
    }

    private String normalizePublicUrlPrefix(String prefix) {
        String normalized = prefix.startsWith("/") || prefix.startsWith("http://") || prefix.startsWith("https://")
                ? prefix
                : "/" + prefix;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
