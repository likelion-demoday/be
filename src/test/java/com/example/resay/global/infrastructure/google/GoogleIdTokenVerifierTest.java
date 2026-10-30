package com.example.resay.global.infrastructure.google;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.global.exception.GeneralException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 가짜 구글 공개키(JWKS) 서버를 실제로 띄우고, 실제 RSA 서명 토큰으로 검증 로직을 확인한다.
 */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "resay-client-id.apps.googleusercontent.com";
    private static final String KEY_ID = "google-test-key";
    private static final String GOOGLE_SUB = "110248495921238986420";

    // 구글 역할의 서명 키와, 같은 키 ID로 서명을 위조하는 공격자 키
    private static RSAKey googleKey;
    private static RSAKey attackerKey;
    private static HttpServer jwksServer;
    private static final AtomicInteger jwksRequestCount = new AtomicInteger();

    @BeforeAll
    static void startGoogleJwksServer() throws Exception {
        googleKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        attackerKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        byte[] jwks = new JWKSet(googleKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/certs", exchange -> {
            jwksRequestCount.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(jwks);
            }
        });
        jwksServer.start();
    }

    @AfterAll
    static void stopGoogleJwksServer() {
        jwksServer.stop(0);
    }

    @Test
    void returnsUserInfoFromValidToken() {
        GoogleUserInfo user = verifier().verify(token(googleKey, claims -> { }));

        assertThat(user.providerId()).isEqualTo(GOOGLE_SUB);
        assertThat(user.email()).isEqualTo("user@gmail.com");
        assertThat(user.name()).isEqualTo("홍길동");
    }

    @Test
    void rejectsTokenIssuedForAnotherApp() {
        // aud 검증이 빠지면 다른 서비스용 구글 토큰으로 우리 서비스에 로그인할 수 있다
        String tokenForAnotherApp = token(googleKey,
                claims -> claims.audience(List.of("another-app.apps.googleusercontent.com")));

        assertFailsWith(AuthErrorCode.SOCIAL_LOGIN_FAILED, () -> verifier().verify(tokenForAnotherApp));
    }

    @Test
    void rejectsForgedSignature() {
        String forged = token(attackerKey, claims -> { });

        assertFailsWith(AuthErrorCode.SOCIAL_LOGIN_FAILED, () -> verifier().verify(forged));
    }

    @Test
    void rejectsAlgorithmSwitchToHmac() {
        // RS256 대신 HS256으로 바꿔 서명하는 JWT 알고리즘 혼동 공격
        byte[] secret = "an-attacker-chosen-shared-secret-32bytes!".getBytes(StandardCharsets.UTF_8);
        // 공격자는 구글 공개키의 키 ID를 헤더에 그대로 넣는다
        NimbusJwtEncoder hmacEncoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .jwkPostProcessor(jwk -> jwk.keyID(KEY_ID))
                .build();
        String hmacToken = hmacEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).keyId(KEY_ID).build(),
                validClaims().build()
        )).getTokenValue();

        assertFailsWith(AuthErrorCode.SOCIAL_LOGIN_FAILED, () -> verifier().verify(hmacToken));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        String token = token(googleKey, claims -> claims.issuer("https://evil.example.com"));

        assertFailsWith(AuthErrorCode.SOCIAL_LOGIN_FAILED, () -> verifier().verify(token));
    }

    @Test
    void acceptsGoogleIssuerWithoutScheme() {
        String token = token(googleKey, claims -> claims.issuer("accounts.google.com"));

        assertThat(verifier().verify(token).providerId()).isEqualTo(GOOGLE_SUB);
    }

    @Test
    void rejectsExpiredToken() {
        Instant issuedAt = Instant.now().minusSeconds(7200);
        String expired = token(googleKey, claims -> claims
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600)));

        assertFailsWith(AuthErrorCode.SOCIAL_LOGIN_FAILED, () -> verifier().verify(expired));
    }

    @Test
    void rejectsTokenWithoutSubject() {
        String token = token(googleKey, claims -> claims.claims(map -> map.remove("sub")));

        assertFailsWith(AuthErrorCode.SOCIAL_LOGIN_FAILED, () -> verifier().verify(token));
    }

    @Test
    void rejectsMalformedToken() {
        assertFailsWith(AuthErrorCode.SOCIAL_LOGIN_FAILED, () -> verifier().verify("not-a-jwt"));
    }

    @Test
    void ignoresEmailNotVerifiedByGoogle() {
        String unverified = token(googleKey, claims -> claims.claim("email_verified", false));
        String missing = token(googleKey, claims -> claims.claims(map -> map.remove("email_verified")));

        assertThat(verifier().verify(unverified).email()).isNull();
        assertThat(verifier().verify(missing).email()).isNull();
    }

    @Test
    void cachesGooglePublicKeysAcrossLogins() {
        GoogleIdTokenVerifier verifier = verifier();
        int before = jwksRequestCount.get();

        for (int i = 0; i < 3; i++) {
            verifier.verify(token(googleKey, claims -> { }));
        }

        // 로그인마다 구글 서버를 호출하지 않는다
        assertThat(jwksRequestCount.get() - before).isEqualTo(1);
    }

    @Test
    void reportsProviderErrorWhenGooglePublicKeysAreUnreachable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        GoogleIdTokenVerifier unreachable = new GoogleIdTokenVerifier(
                new GoogleProperties(CLIENT_ID, "http://127.0.0.1:" + closedPort + "/certs"));

        assertFailsWith(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE,
                () -> unreachable.verify(token(googleKey, claims -> { })));
    }

    @Test
    void rejectsWhenClientIdIsNotConfigured() {
        GoogleIdTokenVerifier notConfigured = new GoogleIdTokenVerifier(new GoogleProperties("", jwksUri()));

        assertFailsWith(AuthErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED,
                () -> notConfigured.verify(token(googleKey, claims -> { })));
    }

    private GoogleIdTokenVerifier verifier() {
        return new GoogleIdTokenVerifier(new GoogleProperties(CLIENT_ID, jwksUri()));
    }

    private static String jwksUri() {
        return "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/certs";
    }

    private static JwtClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .subject(GOOGLE_SUB)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .claim("name", "홍길동");
    }

    private static String token(RSAKey signingKey, Consumer<JwtClaimsSet.Builder> customizer) {
        JwtClaimsSet.Builder claims = validClaims();
        customizer.accept(claims);
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build(),
                claims.build()
        )).getTokenValue();
    }

    private static void assertFailsWith(AuthErrorCode expected, Executable executable) {
        GeneralException exception = assertThrows(GeneralException.class, executable);
        assertThat(exception.getErrorCode()).isEqualTo(expected);
    }
}
