package com.example.resay.domain.user.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createLocalRejectsBlankEmail(String email) {
        assertThatThrownBy(() -> User.createLocal(email, "encoded-password", "닉네임"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createLocalRejectsBlankPassword(String encodedPassword) {
        assertThatThrownBy(() -> User.createLocal("user@example.com", encodedPassword, "닉네임"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createLocalRejectsBlankNickname(String nickname) {
        assertThatThrownBy(() -> User.createLocal("user@example.com", "encoded-password", nickname))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSocialRejectsNullProvider() {
        assertThatThrownBy(() -> User.createSocial(null, "12345", null, "닉네임"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createSocialRejectsBlankProviderId(String providerId) {
        assertThatThrownBy(() -> User.createSocial(Provider.KAKAO, providerId, null, "닉네임"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createSocialRejectsBlankNickname(String nickname) {
        assertThatThrownBy(() -> User.createSocial(Provider.KAKAO, "12345", null, nickname))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void createSocialRejectsBlankButNonNullEmail(String email) {
        assertThatThrownBy(() -> User.createSocial(Provider.KAKAO, "12345", email, "닉네임"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
