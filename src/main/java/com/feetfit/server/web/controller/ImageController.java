package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.service.ImageService.ImageUploadService;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Image", description = "이미지 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageController {

    private final ImageUploadService imageUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "이미지 업로드 [은서]",
            description = """
                    EC2 서버에서 AWS CLI를 사용해 이미지를 S3에 업로드합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - folderName은 영문, 숫자, -, _만 허용합니다.
                    - image는 jpg, jpeg, png, webp, gif만 허용합니다.
                    - EC2 서버에 AWS CLI가 설치되어 있고 S3 접근 권한이 있어야 합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이미지 업로드 성공",
                    content = @Content(examples = @ExampleObject(value = UPLOAD_IMAGE_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "폴더 이름 누락, 이미지 파일 누락, 지원하지 않는 파일 형식",
                    content = @Content(examples = @ExampleObject(value = UPLOAD_IMAGE_BAD_REQUEST_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "AWS CLI 미설치, S3 권한 없음, S3 업로드 실패 또는 서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = UPLOAD_IMAGE_INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ImageResponseDTO.UploadImageResultDTO> uploadImage(
            @RequestParam String folderName,
            @RequestParam MultipartFile image
    ) {
        return ApiResponse.onSuccess(imageUploadService.upload(folderName, image));
    }

    private static final String UPLOAD_IMAGE_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "folderName": "reports",
                "originalFileName": "left-foot.png",
                "storedFileName": "7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png",
                "contentType": "image/png",
                "size": 152340,
                "bucketName": "project5-42-oregon-feetfit-s3",
                "s3Key": "reports/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png",
                "s3Uri": "s3://project5-42-oregon-feetfit-s3/reports/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png",
                "imageUrl": "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/reports/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png"
              }
            }
            """;

    private static final String UPLOAD_IMAGE_BAD_REQUEST_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "이미지 파일만 업로드할 수 있습니다.",
              "result": null
            }
            """;

    private static final String UPLOAD_IMAGE_INTERNAL_SERVER_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON500",
              "message": "EC2 서버에 AWS CLI가 설치되어 있지 않거나 실행할 수 없습니다.",
              "result": null
            }
            """;
}
