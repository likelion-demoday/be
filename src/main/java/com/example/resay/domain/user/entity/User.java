package com.example.resay.domain.user.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(
                        name = "uk_users_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 카카오 계정은 이메일 제공에 동의하지 않을 수 있어 nullable
    @Column(length = 100)
    private String email;

    // 소셜 가입 계정은 비밀번호가 없다
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    private User(
            String email,
            String password,
            String nickname,
            Provider provider,
            String providerId
    ) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.role = Role.USER;
    }

    public static User createLocal(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname, Provider.LOCAL, null);
    }

    public static User createSocial(
            Provider provider,
            String providerId,
            String email,
            String nickname
    ) {
        if (provider == Provider.LOCAL) {
            throw new IllegalArgumentException("소셜 가입에는 LOCAL 가입 경로를 사용할 수 없습니다.");
        }
        return new User(email, null, nickname, provider, providerId);
    }
}
