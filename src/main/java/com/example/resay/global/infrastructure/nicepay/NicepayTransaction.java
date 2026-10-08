package com.example.resay.global.infrastructure.nicepay;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 나이스페이가 알려 준 거래 상태 (승인 · 망취소 · 조회 응답 공통).
 * 카드 번호 같은 결제 수단 정보는 담지 않는다.
 *
 * @param httpStatus 응답의 HTTP 상태. 없는 거래는 404, 키가 틀리면 401과 함께 결과 코드가 온다
 * @param resultCode 0000이면 요청 성공, 그 외는 실패
 * @param status     paid, ready, failed, cancelled, partialCancelled, expired (요청이 실패하면 비어 있다)
 * @param paidAt     결제 완료 시각 (한국 시간). 결제 완료가 아니면 null
 * @param signature  응답에 실린 서명을 우리 시크릿 키로 확인한 결과
 */
public record NicepayTransaction(
        int httpStatus,
        String resultCode,
        String resultMsg,
        String tid,
        String orderId,
        String status,
        int amount,
        LocalDateTime paidAt,
        String receiptUrl,
        Signature signature
) {

    public enum Signature {
        VALID,
        // 서명이 실려 왔는데 우리가 계산한 값과 다르다
        INVALID,
        // 서명이나 서명 계산에 필요한 값(ediDate)이 응답에 없다. 매뉴얼상 둘 다 필수 항목이 아니다
        MISSING
    }

    private static final String SUCCESS_CODE = "0000";
    private static final int HTTP_NOT_FOUND = 404;
    private static final String NOT_FOUND_CODE = "U107";
    private static final String STATUS_PAID = "paid";
    private static final String STATUS_CANCELLED = "cancelled";
    // 결제되지 않았음이 분명한 상태 (ready: 인증만 하고 승인되지 않음)
    private static final Set<String> NOT_PAID_STATUSES = Set.of("ready", "failed", STATUS_CANCELLED, "expired");

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(resultCode);
    }

    public boolean isPaid() {
        return isSuccess() && STATUS_PAID.equals(status);
    }

    /** 조회 결과가 "결제되지 않았다"로 분명한지. 처음 보는 상태 값이나 조회 거절(키 오류 등)은 해당하지 않는다. */
    public boolean isNotPaid() {
        return isNotFound() || (isSuccess() && status != null && NOT_PAID_STATUSES.contains(status));
    }

    /** 망취소가 받아들여졌는지 (결제됐던 거래가 취소됐다). */
    public boolean isCancelled() {
        return isSuccess() && STATUS_CANCELLED.equals(status);
    }

    /**
     * 나이스페이가 요청한 대상(tid)을 찾지 못했다고 답했는지 (HTTP 404).
     * 승인 요청에서는 "그런 인증 내역이 없다"(U121)는 뜻이다. 인증 직후에는 내역이 아직 보이지 않아 이 답이 올 수 있다.
     */
    public boolean isMissing() {
        return httpStatus == HTTP_NOT_FOUND;
    }

    // 그런 거래가 없다 (샌드박스에서 확인: HTTP 404 + 결과 코드 U107).
    // 404만으로 판단하지 않는다. 주소 설정이 틀려서 나는 404(페이지 없음)를 "결제되지 않음"으로 읽으면 결제된 주문을 실패로 끝내게 된다
    public boolean isNotFound() {
        return httpStatus == HTTP_NOT_FOUND && NOT_FOUND_CODE.equals(resultCode);
    }
}
