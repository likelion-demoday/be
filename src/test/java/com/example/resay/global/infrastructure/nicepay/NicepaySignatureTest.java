package com.example.resay.global.infrastructure.nicepay;

import com.example.resay.global.infrastructure.nicepay.NicepayTransaction.Signature;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 기대값은 테스트 밖에서(파이썬 hashlib) 계산한 고정값이다. 같은 코드로 다시 계산해 비교하면 공식이 틀려도 통과한다
class NicepaySignatureTest {

    private static final String CLIENT_KEY = "S2_test-client-key";
    private static final String AUTH_TOKEN = "NICETOKEN0123456789";
    // sha256("NICETOKEN0123456789" + "S2_test-client-key" + "3000" + "test-secret-key")
    private static final String AUTH_SIGNATURE = "921075cc94396d7ce50a447b7debf253031555416773d21223da8492a022301b";
    private static final String TID = "UT0000113m01012610081530120001";
    private static final String EDI_DATE = "2026-10-08T15:30:12.000+0900";
    // sha256("UT0000113m01012610081530120001" + "3000" + "2026-10-08T15:30:12.000+0900" + "test-secret-key")
    private static final String TRANSACTION_SIGNATURE =
            "c81452b64c538ec59e1915c556f288a5c535a4fff90b53b6f4cc7baa6abe151e";

    private final NicepaySignature signature = new NicepaySignature(properties("test-secret-key"));

    @Test
    void acceptsAuthResultSignedWithOurSecretKey() {
        assertThat(signature.matchesAuthResult(AUTH_TOKEN, CLIENT_KEY, 3000, AUTH_SIGNATURE)).isTrue();
        // 16진수의 대소문자는 가리지 않는다
        assertThat(signature.matchesAuthResult(AUTH_TOKEN, CLIENT_KEY, 3000, AUTH_SIGNATURE.toUpperCase())).isTrue();
    }

    // 싼 상품으로 인증받은 결과를 비싼 주문에 쓰려는 경우
    @Test
    void rejectsAuthResultWhenAmountDiffers() {
        assertThat(signature.matchesAuthResult(AUTH_TOKEN, CLIENT_KEY, 10000, AUTH_SIGNATURE)).isFalse();
    }

    @Test
    void rejectsAuthResultWhenAnySignedValueDiffers() {
        assertThat(signature.matchesAuthResult("OTHERTOKEN", CLIENT_KEY, 3000, AUTH_SIGNATURE)).isFalse();
        assertThat(signature.matchesAuthResult(AUTH_TOKEN, "S2_other-client", 3000, AUTH_SIGNATURE)).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedSignature() {
        assertThat(signature.matchesAuthResult(AUTH_TOKEN, CLIENT_KEY, 3000, null)).isFalse();
        assertThat(signature.matchesAuthResult(AUTH_TOKEN, CLIENT_KEY, 3000, "")).isFalse();
        assertThat(signature.matchesAuthResult(AUTH_TOKEN, CLIENT_KEY, 3000, "not-a-signature")).isFalse();
        assertThat(signature.matchesAuthResult(null, CLIENT_KEY, 3000, AUTH_SIGNATURE)).isFalse();
        assertThat(signature.matchesAuthResult(AUTH_TOKEN, null, 3000, AUTH_SIGNATURE)).isFalse();
    }

    // 시크릿 키를 모르는 쪽이 만든 서명
    @Test
    void rejectsSignatureMadeWithAnotherSecretKey() {
        NicepaySignature other = new NicepaySignature(properties("another-secret-key"));

        assertThat(other.matchesAuthResult(AUTH_TOKEN, CLIENT_KEY, 3000, AUTH_SIGNATURE)).isFalse();
    }

    @Test
    void verifiesTransactionSignature() {
        assertThat(signature.checkTransaction(TID, 3000, EDI_DATE, TRANSACTION_SIGNATURE)).isEqualTo(Signature.VALID);
        assertThat(signature.checkTransaction(TID, 3001, EDI_DATE, TRANSACTION_SIGNATURE)).isEqualTo(Signature.INVALID);
        assertThat(signature.checkTransaction("UT0000113m01012610081530120002", 3000, EDI_DATE, TRANSACTION_SIGNATURE))
                .isEqualTo(Signature.INVALID);
        // 서명이나 ediDate가 아예 없는 응답은 "틀림"과 구분한다
        assertThat(signature.checkTransaction(TID, 3000, null, TRANSACTION_SIGNATURE)).isEqualTo(Signature.MISSING);
        assertThat(signature.checkTransaction(TID, 3000, EDI_DATE, null)).isEqualTo(Signature.MISSING);
        assertThat(signature.checkTransaction(TID, 3000, EDI_DATE, " ")).isEqualTo(Signature.MISSING);
    }

    // 인증 토큰은 저장하지 않고 지문만 남긴다. 같은 토큰은 같은 지문, 다른 토큰은 다른 지문
    @Test
    void fingerprintsAuthTokenWithoutKeepingIt() {
        String fingerprint = signature.fingerprint(AUTH_TOKEN);

        assertThat(fingerprint).hasSize(64).doesNotContain(AUTH_TOKEN);
        assertThat(signature.fingerprint(AUTH_TOKEN)).isEqualTo(fingerprint);
        assertThat(signature.fingerprint(AUTH_TOKEN + "1")).isNotEqualTo(fingerprint);
    }

    // 나이스페이 공식 매뉴얼(README)에 실린 예시 값이다. 공식을 잘못 이해했다면 여기서 걸린다.
    // 금액은 십진 정수 그대로, ediDate는 응답 문자열 그대로(밀리초와 +0900 포함), 구분자 없이 이어 붙인다
    @Test
    void matchesExamplesInOfficialManual() {
        String manualClientKey = "S2_af4543a0be4d49a98122e01ec2059a56";
        NicepaySignature manual = new NicepaySignature(new NicepayProperties(
                manualClientKey, "9eb85607103646da9f9c02b128f2e5ee", "", "http://localhost:3000/serverAuth",
                Duration.ofSeconds(5), Duration.ofSeconds(30)));

        assertThat(manual.matchesAuthResult(
                "NICEUNTTB06096FF8F653AA366E7EEED1101AAAE", manualClientKey, 1004,
                "99ea68bf15681741e793ece56ab87891b9bdc94cd54abdcb55b2884f4336155a")).isTrue();
        assertThat(manual.checkTransaction(
                "UT0000113m01012111051714341073", 1004, "2021-11-05T17:14:35.150+0900",
                "63b251b31c909eebef1a9f4fcc19e77bdcb8f64fc1066a29670f8627186865cd")).isEqualTo(Signature.VALID);
    }

    // 키가 설정되지 않은 서버에서는 어떤 서명도 통과하지 않는다
    @Test
    void rejectsEverythingWhenNotConfigured() {
        NicepaySignature unconfigured = new NicepaySignature(properties(""));
        // sha256("NICETOKEN0123456789" + "S2_test-client-key" + "3000" + "")
        String signedWithEmptyKey = NicepaySignature.sha256Hex(AUTH_TOKEN + CLIENT_KEY + 3000);

        assertThat(unconfigured.matchesAuthResult(AUTH_TOKEN, CLIENT_KEY, 3000, signedWithEmptyKey)).isFalse();
    }

    private static NicepayProperties properties(String secretKey) {
        return new NicepayProperties(
                CLIENT_KEY, secretKey, "", "http://localhost:8080/api/v1/payments/nicepay/return",
                Duration.ofSeconds(5), Duration.ofSeconds(30));
    }
}
