package com.feetfit.server.web.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
public class ImageResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "이미지 업로드 응답")
    public static class UploadImageResultDTO {

        @Schema(description = "저장 폴더 이름", example = "reports")
        private String folderName;

        @Schema(description = "원본 파일 이름", example = "left-foot.png")
        private String originalFileName;

        @Schema(description = "서버에 저장된 파일 이름", example = "7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png")
        private String storedFileName;

        @Schema(description = "파일 MIME 타입", example = "image/png")
        private String contentType;

        @Schema(description = "파일 크기(byte)", example = "152340")
        private Long size;

        @Schema(description = "S3 버킷 이름", example = "project5-42-oregon-feetfit-s3")
        private String bucketName;

        @Schema(description = "S3 객체 키", example = "reports/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png")
        private String s3Key;

        @Schema(description = "S3 URI", example = "s3://project5-42-oregon-feetfit-s3/reports/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png")
        private String s3Uri;

        @Schema(description = "업로드된 이미지 접근 URL", example = "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/reports/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png")
        private String imageUrl;
    }
}
