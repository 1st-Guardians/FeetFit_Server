package com.feetfit.server.service.HealthArticleService;

import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.domain.HealthArticle;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.UserHealthArticle;
import com.feetfit.server.domain.enums.HealthType;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.UserHealthArticleRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.health.HealthArticleResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HealthArticleServiceImplTest {

    @Mock
    private UserHealthArticleRepository userHealthArticleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HealthArticleServiceImpl healthArticleService;

    @Test
    void getMyHealthArticles_existingUser_returnsArticles() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(userHealthArticleRepository.findAllByUserIdWithArticle(1L))
                .willReturn(List.of(userHealthArticle()));

        HealthArticleResponseDTO.HealthArticleListResponseDTO response =
                healthArticleService.getMyHealthArticles(1L);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getArticles()).hasSize(1);
        assertThat(response.getArticles().get(0).getArticleId()).isEqualTo(1L);
        assertThat(response.getArticles().get(0).getTitle()).isEqualTo("무지외반증 예방을 위한 발 관리");
        assertThat(response.getArticles().get(0).getHealthType()).isEqualTo("HALLUX_VALGUS");
    }

    @Test
    void getMyHealthArticles_missingUser_throwsUserHandler() {
        given(userRepository.existsById(404L)).willReturn(false);

        assertThatThrownBy(() -> healthArticleService.getMyHealthArticles(404L))
                .isInstanceOf(UserHandler.class);
    }

    private static UserHealthArticle userHealthArticle() {
        return UserHealthArticle.builder()
                .id(1L)
                .user(user())
                .healthArticle(healthArticle())
                .build();
    }

    private static User user() {
        return User.builder()
                .id(1L)
                .nickname("은서")
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static HealthArticle healthArticle() {
        return HealthArticle.builder()
                .id(1L)
                .title("무지외반증 예방을 위한 발 관리")
                .url("https://example.com/articles/hallux-valgus-care")
                .publisher("FeetFit")
                .publishedAt(LocalDateTime.of(2026, 5, 20, 9, 0))
                .healthType(HealthType.HALLUX_VALGUS)
                .description("무지외반증 예방을 위한 생활 습관과 스트레칭 정보를 제공합니다.")
                .build();
    }
}
