package com.feetfit.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${swagger.server-url:http://localhost:8080}")
    private String serverUrl;

    @Value("${swagger.server-description:Local server}")
    private String serverDescription;

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
                .addServersItem(new Server()
                        .url(serverUrl)
                        .description(serverDescription))
                .addSecurityItem(securityRequirement)
                .components(components)
                .info(new Info()
                        .title("FeetFit API")
                        .description("FeetFit 발 건강 관리 서비스 API")
                        .version("1.0.0"));
    }
}
