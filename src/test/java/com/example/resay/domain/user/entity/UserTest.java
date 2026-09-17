package com.example.resay.domain.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void createLocalBuildsLocalUserWithDefaultRole() {
        User user = User.createLocal("user@example.com", "encoded-password", "닉네임");

        assertThat(user.getProvider()).isEqualTo(Provider.LOCAL);
        assertThat(user.getProviderId()).isNull();
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void createSocialBuildsUserWithoutPassword() {
        User user = User.createSocial(Provider.KAKAO, "12345", null, "카카오유저");

        assertThat(user.getProvider()).isEqualTo(Provider.KAKAO);
        assertThat(user.getProviderId()).isEqualTo("12345");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPassword()).isNull();
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void createSocialRejectsLocalProvider() {
        assertThatThrownBy(() -> User.createSocial(Provider.LOCAL, "12345", null, "닉네임"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
