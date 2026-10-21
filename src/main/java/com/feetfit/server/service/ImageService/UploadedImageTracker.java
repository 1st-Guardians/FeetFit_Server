package com.feetfit.server.service.ImageService;

import com.feetfit.server.web.dto.image.ImageResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

public final class UploadedImageTracker {
    private static final String ATTRIBUTE = UploadedImageTracker.class.getName();
    private UploadedImageTracker() { }

    public static ImageResponseDTO.UploadImageResultDTO record(ImageResponseDTO.UploadImageResultDTO upload) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getRequestURI().startsWith("/api/reports/")) {
            uploads(attributes.getRequest()).add(upload);
        }
        return upload;
    }

    @SuppressWarnings("unchecked")
    public static List<ImageResponseDTO.UploadImageResultDTO> uploads(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value == null) {
            value = new ArrayList<ImageResponseDTO.UploadImageResultDTO>();
            request.setAttribute(ATTRIBUTE, value);
        }
        return (List<ImageResponseDTO.UploadImageResultDTO>) value;
    }
}
