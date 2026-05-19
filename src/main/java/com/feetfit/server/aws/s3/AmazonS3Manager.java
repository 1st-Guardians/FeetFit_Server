package com.feetfit.server.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.feetfit.server.config.S3Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmazonS3Manager {

    private final AmazonS3 amazonS3;
    private final S3Config s3Config;

    public String uploadFile(MultipartFile file, String dirPath) {
        String fileName = dirPath + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try {
            amazonS3.putObject(s3Config.getBucket(), fileName, file.getInputStream(), metadata);
        } catch (IOException e) {
            log.error("S3 upload failed: {}", e.getMessage());
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        }
        return amazonS3.getUrl(s3Config.getBucket(), fileName).toString();
    }

    public void deleteFile(String fileUrl) {
        String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        amazonS3.deleteObject(s3Config.getBucket(), fileName);
    }
}
