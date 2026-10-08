package com.example.resay.domain.payment.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {

    INVALID_PRODUCT(HttpStatus.BAD_REQUEST, "PAYMENT400_1", "충전할 수 없는 상품입니다."),
    // 결제가 끝난 뒤 돌아갈 프론트 주소를 요청의 Origin 헤더로 정한다. 그 값이 없는 경우(브라우저가 아닌 호출)나
    // 프론트 주소가 아닌 경우(Swagger처럼 백엔드와 같은 주소에서 부른 호출)다. 다른 사이트의 요청은 그 전에 CORS에서 403으로 막힌다
    INVALID_ORIGIN(HttpStatus.BAD_REQUEST, "PAYMENT400_2", "결제를 시작할 수 없는 주소에서 온 요청입니다."),
    // 결제창이 보낸 인증 결과에 주문번호가 없거나 모르는 주문인 경우
    INVALID_AUTH_RESULT(HttpStatus.BAD_REQUEST, "PAYMENT400_3", "결제 결과를 확인할 수 없습니다."),
    // 테스트 상점 키로 동작하는 서버에서 운영자가 아닌 사용자가 충전하려는 경우 (런칭 전 테스트 기간)
    TEST_PAYMENT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "PAYMENT403_1", "지금은 충전할 수 없습니다. 서비스 준비 중입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_1", "충전 주문을 찾을 수 없습니다."),
    // 나이스페이 키가 없거나 Server 승인 방식의 키가 아닌 서버
    PAYMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT503_1", "지금은 충전할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
