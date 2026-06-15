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

import java.net.URI;

@Configuration
public class SwaggerConfig {

    @Value("${swagger.local-server-url:http://localhost:8080}")
    private String localServerUrl;

    @Value("${swagger.deploy-server-url:http://54.184.58.176}")
    private String deployServerUrl;

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
                        .url(localServerUrl)
                        .description("Local server"))
                .addServersItem(new Server()
                        .url(stripPort(deployServerUrl))
                        .description("Deploy server"))
                .addSecurityItem(securityRequirement)
                .components(components)
                .info(new Info()
                        .title("FeetFit API")
                        .description("FeetFit 발 건강 관리 서비스 API")
                        .version("1.0.0"));
    }

    private String stripPort(String serverUrl) {
        URI uri = URI.create(serverUrl);
        String host = uri.getHost();

        if (host == null) {
            return serverUrl;
        }

        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return uri.getScheme() + "://" + host + path;
    }
}
