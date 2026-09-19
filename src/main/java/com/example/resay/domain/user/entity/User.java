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
import org.springframework.util.StringUtils;

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

    // MySQL에서 enum 매핑은 네이티브 enum 컬럼이나 CHECK 제약조건으로 생성되어
    // 값을 추가할 때마다 ALTER가 필요해진다. 순수 varchar로 고정한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Provider provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
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
        requireText(email, "email");
        requireText(encodedPassword, "encodedPassword");
        requireText(nickname, "nickname");
        return new User(email, encodedPassword, nickname, Provider.LOCAL, null);
    }

    public static User createSocial(
            Provider provider,
            String providerId,
            String email,
            String nickname
    ) {
        if (provider == null || provider == Provider.LOCAL) {
            throw new IllegalArgumentException("소셜 가입에는 LOCAL이 아닌 가입 경로가 필요합니다.");
        }
        // MySQL UNIQUE는 NULL 중복을 막지 못하므로 (provider, NULL) 계정이 여러 건 생기지 않게 여기서 막는다
        requireText(providerId, "providerId");
        requireText(nickname, "nickname");
        // 이메일 제공에 동의하지 않은 소셜 계정은 null로 저장한다
        if (email != null) {
            requireText(email, "email");
        }
        return new User(email, null, nickname, provider, providerId);
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
    }
}
