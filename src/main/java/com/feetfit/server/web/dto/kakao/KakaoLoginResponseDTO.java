package com.feetfit.server.web.dto.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
public class KakaoLoginResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KakaoUserInfo {
        private Long id;

        @JsonProperty("kakao_account")
        private KakaoAccount kakaoAccount;

        @Getter
        @ToString
        public static class KakaoAccount {
            private String email;
            private Profile profile;

            @Getter
            @ToString
            public static class Profile {
                private String nickname;
            }
        }
    }
}
