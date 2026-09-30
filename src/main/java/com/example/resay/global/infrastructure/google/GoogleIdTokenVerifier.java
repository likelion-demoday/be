package com.example.resay.global.infrastructure.google;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.global.exception.GeneralException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * 프론트가 Google Identity Services로 받은 ID 토큰을 검증한다.
 *
 * <p>검증 항목 (하나라도 빠지면 보안 구멍이 된다)
 * <ul>
 *   <li>서명: 구글 공개키(JWKS)로 RS256 서명 확인. 공개키는 캐시되어 로그인마다 구글을 호출하지 않는다</li>
 *   <li>aud: 우리 Client ID와 일치. 빠지면 <b>다른 서비스용 구글 토큰으로도 로그인</b>된다</li>
 *   <li>iss: 구글이 발급한 토큰인지</li>
 *   <li>exp: 만료 여부</li>
 *   <li>sub: 구글 계정 고유 ID가 있는지</li>
 * </ul>
 */
@Slf4j
@Component
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleIdTokenVerifier {

    // 구글은 두 형태의 발급자를 모두 사용한다
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final String EMAIL_VERIFIED = "email_verified";

    private final JwtDecoder decoder;

    public GoogleIdTokenVerifier(GoogleProperties googleProperties) {
        this.decoder = googleProperties.isConfigured()
                ? createDecoder(googleProperties.clientId(), googleProperties.jwkSetUri())
                : null;
    }

    public GoogleUserInfo verify(String idToken) {
        if (decoder == null) {
            throw new GeneralException(AuthErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException exception) {
            // 위조·만료·다른 앱용 토큰 등. 토큰 원문은 로그에 남기지 않는다
            log.warn("구글 ID 토큰 검증 실패: {}", exception.getMessage());
            throw new GeneralException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
        } catch (JwtException exception) {
            // 구글 공개키를 받아오지 못한 경우 등 우리 쪽에서 판단할 수 없는 실패
            log.error("구글 공개키 조회 실패", exception);
            throw new GeneralException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }

        return new GoogleUserInfo(
                jwt.getSubject(),
                isEmailVerified(jwt) ? jwt.getClaimAsString("email") : null,
                jwt.getClaimAsString("name")
        );
    }

    private static NimbusJwtDecoder createDecoder(String clientId, String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // 기본 검증(만료 등)에 구글 토큰 전용 검증을 더한다
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
                new JwtClaimValidator<>(JwtClaimNames.ISS,
                        issuer -> issuer != null && GOOGLE_ISSUERS.contains(issuer.toString())),
                new JwtAudienceValidator(clientId),
                new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<>(JwtClaimNames.SUB,
                        subject -> subject != null && !subject.toString().isBlank())
        )));
        return decoder;
    }

    // 구글은 boolean으로 주지만, 문자열로 오는 경우도 안전하게 처리한다
    private static boolean isEmailVerified(Jwt jwt) {
        Object value = jwt.getClaim(EMAIL_VERIFIED);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
