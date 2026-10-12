package com.example.resay.domain.payment.service;

import com.example.resay.domain.payment.dto.NicepayReturnRequestDto;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction.Signature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

// 환불 · 웹훅 테스트가 함께 쓰는 나이스페이 응답과 서명
final class NicepayFixtures {

    // src/test/resources/application.yml 의 값
    static final String CLIENT_KEY = "S2_test-client-key";
    static final String SECRET_KEY = "test-secret-key";
    static final String FRONT_ORIGIN = "http://localhost:3000";
    static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 12, 15, 30, 12);
    static final LocalDateTime CANCELLED_AT = LocalDateTime.of(2026, 10, 12, 16, 0, 5);

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private NicepayFixtures() {
    }

    static long nextSequence() {
        return SEQUENCE.incrementAndGet();
    }

    static Payment newOrder(Long userId, int amount) {
        return Payment.create(
                userId, "RS261012" + String.format("%032x", nextSequence()),
                "CREDIT_" + amount, amount, amount, FRONT_ORIGIN);
    }

    static String newTid() {
        return "UT0000113m0101261012" + String.format("%010d", nextSequence());
    }

    // 결제창이 보내는 인증 결과. 인증 결과마다 토큰이 다르다 (같은 토큰은 한 주문에만 쓸 수 있다)
    static NicepayReturnRequestDto authResult(Payment order, String tid) {
        String authToken = "NICETOKEN" + tid;
        return new NicepayReturnRequestDto(
                "0000", "인증 성공", tid, CLIENT_KEY, order.getOrderId(), String.valueOf(order.getAmount()),
                authToken, sign(authToken + CLIENT_KEY + order.getAmount() + SECRET_KEY));
    }

    static NicepayTransaction paid(Payment order, String tid) {
        return new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", tid, order.getOrderId(), "paid", order.getAmount(),
                PAID_AT, null, null, Signature.VALID);
    }

    // 전액 취소된 거래 (취소 요청의 응답, 또는 취소된 뒤의 조회 결과)
    static NicepayTransaction cancelled(Payment order) {
        return cancelled(order, Signature.VALID);
    }

    static NicepayTransaction cancelled(Payment order, Signature signature) {
        return new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", order.getTid(), order.getOrderId(), "cancelled", order.getAmount(),
                PAID_AT, CANCELLED_AT, null, signature);
    }

    // 요청은 성공(0000)이고 거래 상태만 다른 조회 결과
    static NicepayTransaction found(Payment order, String nicepayStatus) {
        return new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", order.getTid(), order.getOrderId(), nicepayStatus,
                order.getAmount(), PAID_AT, null, null, Signature.VALID);
    }

    static NicepayTransaction rejected(String resultCode, String resultMsg) {
        return new NicepayTransaction(200, resultCode, resultMsg, "", "", "", 0, null, null, null, Signature.MISSING);
    }

    // 그런 거래가 없다는 답 (샌드박스에서 확인한 형식: HTTP 404 + U107)
    static NicepayTransaction notFound() {
        return new NicepayTransaction(
                404, "U107", "거래내역이 존재 하지 않습니다.", "", "", "", 0, null, null, null, Signature.MISSING);
    }

    static String sign(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
