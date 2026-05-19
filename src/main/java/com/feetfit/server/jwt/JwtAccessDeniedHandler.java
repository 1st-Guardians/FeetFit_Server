package com.feetfit.server.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        ApiResponse<Object> body = ApiResponse.onFailure("COMMON403", "금지된 요청입니다.", null);
        response.getWriter().write(new ObjectMapper().writeValueAsString(body));
    }
}
