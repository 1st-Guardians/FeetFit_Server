package com.feetfit.server.web.controller;

import com.feetfit.server.config.SecurityConfig;
import com.feetfit.server.jwt.*;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.service.UserService.UserService;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = UserController.class, properties = "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Import({SecurityConfig.class, JwtFilter.class, TokenProvider.class, JwtAccessDeniedHandler.class,
        JwtAuthenticationEntryPoint.class, FindLoginUser.class, ExceptionAdvice.class})
class UserWithdrawalSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired TokenProvider tokens;
    @MockBean UserService service;
    @MockBean UserRepository users;
    @MockBean(name = "jpaMappingContext") JpaMetamodelMappingContext jpaMappingContext;
    private AtomicBoolean active;

    @BeforeEach
    void setup() {
        active = new AtomicBoolean(true);
        when(users.existsByIdAndStatus(7L, UserStatus.ACTIVE)).thenAnswer(call -> active.get());
        when(service.withdraw(eq(7L), any())).thenAnswer(call -> {
            active.set(false);
            return UserResponseDTO.WithdrawUserResultDTO.builder().userId(7L).deleted(true).cleanupPending(true)
                    .socialUnlinkStatus("PENDING").build();
        });
    }

    @Test
    void deleteUsesJwtOwnerAndImmediatelyRejectsBothOldServiceTokens() throws Exception {
        String access = tokens.createAccessToken(7L);
        String refresh = tokens.createRefreshToken(7L);
        mvc.perform(delete("/api/users/withdraw").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.userId").value(7))
                .andExpect(jsonPath("$.result.deleted").value(true));
        mvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
        verify(service).withdraw(7L, null);
        verify(service, never()).getProfile(any());
    }

    @Test
    void suppliedUserIdCannotSelectAnotherAccount() throws Exception {
        mvc.perform(delete("/api/users/withdraw").header("Authorization", "Bearer " + tokens.createAccessToken(7L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.userId").value(7));
        verify(service).withdraw(eq(7L), any());
        verify(service, never()).withdraw(eq(999L), any());
    }

    @Test
    void unauthenticatedAndDeletedAccountsCannotInvokeDeletion() throws Exception {
        mvc.perform(delete("/api/users/withdraw")).andExpect(status().isUnauthorized());
        active.set(false);
        mvc.perform(delete("/api/users/withdraw").header("Authorization", "Bearer " + tokens.createAccessToken(7L)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
