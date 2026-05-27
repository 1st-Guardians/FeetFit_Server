package com.feetfit.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload.root-path:uploads}")
    private String uploadRootPath;

    @Value("${file.upload.public-url-prefix:/uploads}")
    private String publicUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadRootPath).toAbsolutePath().normalize();
        String resourceLocation = uploadPath.toUri().toString();
        String resourcePattern = normalizePublicUrlPrefix(publicUrlPrefix) + "/**";

        registry.addResourceHandler(resourcePattern)
                .addResourceLocations(resourceLocation);
    }

    private String normalizePublicUrlPrefix(String prefix) {
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
