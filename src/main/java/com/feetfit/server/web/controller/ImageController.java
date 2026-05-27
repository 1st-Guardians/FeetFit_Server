package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.service.ImageService.ImageUploadService;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
                    배포 서버 로컬 파일시스템에 이미지를 업로드합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - 배포 서버 Host로 요청한 경우에만 업로드할 수 있습니다.
                    - folderName은 영문, 숫자, -, _만 허용합니다.
                    - image는 jpg, jpeg, png, webp, gif만 허용합니다.
                    - 업로드 후 imageUrl 경로로 이미지에 접근할 수 있습니다.
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
                    responseCode = "403",
                    description = "로컬 서버 등 허용되지 않은 Host에서 업로드 요청",
                    content = @Content(examples = @ExampleObject(value = UPLOAD_IMAGE_FORBIDDEN_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "이미지 저장 실패 또는 서버 내부 오류"
            )
    })
    public ApiResponse<ImageResponseDTO.UploadImageResultDTO> uploadImage(
            HttpServletRequest request,
            @RequestParam String folderName,
            @RequestParam MultipartFile image
    ) {
        return ApiResponse.onSuccess(imageUploadService.upload(request.getHeader("Host"), folderName, image));
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
                "imageUrl": "/uploads/reports/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png"
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

    private static final String UPLOAD_IMAGE_FORBIDDEN_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON403",
              "message": "배포 서버에서만 이미지 업로드가 가능합니다.",
              "result": null
            }
            """;
}
