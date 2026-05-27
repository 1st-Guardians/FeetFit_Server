package com.feetfit.server.service.ImageService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Value("${file.upload.root-path:uploads}")
    private String uploadRootPath;

    @Value("${file.upload.public-url-prefix:/uploads}")
    private String publicUrlPrefix;

    @Value("${file.upload.allowed-hosts:54.184.58.176,54.184.58.176:8080}")
    private String allowedHosts;

    public ImageResponseDTO.UploadImageResultDTO upload(String requestHost, String folderName, MultipartFile image) {
        validateDeployServerHost(requestHost);
        validateImage(image);

        String sanitizedFolderName = sanitizeFolderName(folderName);
        String originalFileName = image.getOriginalFilename();
        String extension = extractExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + "." + extension;

        Path folderPath = Paths.get(uploadRootPath).toAbsolutePath().normalize().resolve(sanitizedFolderName);
        Path storedPath = folderPath.resolve(storedFileName).normalize();

        try {
            Files.createDirectories(folderPath);
            image.transferTo(storedPath);
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "이미지 파일 저장에 실패했습니다.");
        }

        String imageUrl = normalizePublicUrlPrefix(publicUrlPrefix) + "/" + sanitizedFolderName + "/" + storedFileName;

        return ImageResponseDTO.UploadImageResultDTO.builder()
                .folderName(sanitizedFolderName)
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .contentType(image.getContentType())
                .size(image.getSize())
                .imageUrl(imageUrl)
                .build();
    }

    private void validateDeployServerHost(String requestHost) {
        if (requestHost == null || requestHost.isBlank()) {
            throw new GeneralException(ErrorStatus._FORBIDDEN, "배포 서버에서만 이미지 업로드가 가능합니다.");
        }

        String normalizedRequestHost = requestHost.trim().toLowerCase();
        Set<String> normalizedAllowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (!normalizedAllowedHosts.contains(normalizedRequestHost)) {
            throw new GeneralException(ErrorStatus._FORBIDDEN, "배포 서버에서만 이미지 업로드가 가능합니다.");
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

    private String normalizePublicUrlPrefix(String prefix) {
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
