package com.example.resay.domain.user.repository;

import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesUserWithAuditingTimestamps() {
        User saved = userRepository.saveAndFlush(
                User.createLocal("user@example.com", "encoded-password", "닉네임")
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsUserByEmail() {
        userRepository.saveAndFlush(User.createLocal("user@example.com", "encoded-password", "닉네임"));

        assertThat(userRepository.findByEmail("user@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("user@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("none@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(User.createLocal("user@example.com", "encoded-password", "닉네임"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.createLocal("user@example.com", "other-password", "다른닉네임")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesMultipleSocialUsersWithoutEmail() {
        userRepository.saveAndFlush(User.createSocial(Provider.KAKAO, "1001", null, "카카오1"));
        userRepository.saveAndFlush(User.createSocial(Provider.KAKAO, "1002", null, "카카오2"));

        assertThat(userRepository.findByProviderAndProviderId(Provider.KAKAO, "1001"))
                .get()
                .extracting(User::getNickname)
                .isEqualTo("카카오1");
        assertThat(userRepository.findByProviderAndProviderId(Provider.KAKAO, "9999")).isEmpty();
    }

    @Test
    void rejectsDuplicateProviderId() {
        userRepository.saveAndFlush(User.createSocial(Provider.KAKAO, "1001", null, "카카오1"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.createSocial(Provider.KAKAO, "1001", null, "카카오2")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
