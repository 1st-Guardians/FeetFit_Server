package com.feetfit.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI feetFitSwaggerAPI() {
        String jwtSchemeName = "JWT TOKEN";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
        return new OpenAPI()
                .addSecurityItem(securityRequirement)
                .components(components)
                .info(new Info()
                        .title("FeetFit API")
                        .description("FeetFit 발 건강 관리 서비스 API")
                        .version("1.0.0"));
    }

    @Bean
    public ServerBaseUrlCustomizer serverBaseUrlCustomizer() {
        return serverBaseUrl -> {
            URI uri = URI.create(serverBaseUrl);
            String host = uri.getHost();

            if (host == null || isLocalHost(host)) {
                return serverBaseUrl;
            }

            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            return uri.getScheme() + "://" + host + path;
        };
    }

    private boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }
}
