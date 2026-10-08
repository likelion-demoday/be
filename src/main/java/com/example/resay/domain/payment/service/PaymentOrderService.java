package com.example.resay.domain.payment.service;

import com.example.resay.domain.payment.code.PaymentErrorCode;
import com.example.resay.domain.payment.config.PaymentProperties;
import com.example.resay.domain.payment.dto.PaymentHistoryResponseDto;
import com.example.resay.domain.payment.dto.PaymentOrderResponseDto;
import com.example.resay.domain.payment.dto.PaymentProductResponseDto;
import com.example.resay.domain.payment.dto.PaymentResponseDto;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.domain.user.entity.Role;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.config.CorsProperties;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.exception.RateLimitExceededException;
import com.example.resay.global.infrastructure.nicepay.NicepayProperties;
import com.example.resay.global.security.ratelimit.SlidingWindowRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.cors.CorsConfiguration;

/**
 * 충전 주문 생성과 조회. 실제 결제(승인)는 PaymentApprovalService가 한다.
 */
@Service
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentOrderService {

    // 결제창에 넘기는 결제 수단. 카드만 받는다
    private static final String PAY_METHOD = "card";
    private static final int MAX_PAGE_SIZE = 50;
    // 한 사용자가 만들 수 있는 주문 수. 결제 버튼을 여러 번 누르는 정도로는 닿지 않는 값이다.
    // 주문은 결제하지 않아도 행으로 남고, 주문마다 승인 요청을 한 번 보낼 수 있어서 무한정 만들게 두지 않는다
    private static final int MAX_ORDERS_PER_WINDOW = 20;
    private static final Duration ORDER_LIMIT_WINDOW = Duration.ofMinutes(10);
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final PaymentRepository paymentRepository;
    private final PaymentProperties paymentProperties;
    private final NicepayProperties nicepayProperties;
    private final UserRepository userRepository;
    // 프론트 주소인지 판단하는 기준. CORS 필터와 같은 규칙(대소문자 · 끝의 '/' 무시)으로 비교하려고 같은 클래스를 쓴다
    private final CorsConfiguration frontOrigins = new CorsConfiguration();
    // 기록은 서버 메모리에 둔다 (서버 1대 기준. 재시작하면 초기화된다)
    private final SlidingWindowRateLimiter orderLimiter =
            new SlidingWindowRateLimiter(MAX_ORDERS_PER_WINDOW, ORDER_LIMIT_WINDOW, Clock.systemUTC());

    public PaymentOrderService(
            PaymentRepository paymentRepository,
            PaymentProperties paymentProperties,
            NicepayProperties nicepayProperties,
            CorsProperties corsProperties,
            UserRepository userRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProperties = paymentProperties;
        this.nicepayProperties = nicepayProperties;
        this.userRepository = userRepository;
        this.frontOrigins.setAllowedOrigins(corsProperties.allowedOrigins());
    }

    public PaymentProductResponseDto getProducts() {
        return PaymentProductResponseDto.from(paymentProperties.products());
    }

    /**
     * 충전 주문을 만들고 결제창에 넘길 값을 돌려준다.
     *
     * @param origin 요청의 Origin 헤더. 결제가 끝나면 이 주소의 결과 화면으로 돌려보낸다.
     *               아무 사이트로나 보내는 통로가 되지 않도록 CORS 허용 목록에 있는 주소만 받는다
     */
    @Transactional
    public PaymentOrderResponseDto create(Long userId, String productCode, String origin) {
        if (!nicepayProperties.canAcceptPayments()) {
            throw new GeneralException(PaymentErrorCode.PAYMENT_UNAVAILABLE);
        }
        PaymentProperties.Product product = paymentProperties.findProduct(productCode)
                .orElseThrow(() -> new GeneralException(PaymentErrorCode.INVALID_PRODUCT));
        if (!isFrontOrigin(origin)) {
            throw new GeneralException(PaymentErrorCode.INVALID_ORIGIN);
        }
        if (isRestrictedTestPayment() && !isAdmin(userId)) {
            throw new GeneralException(PaymentErrorCode.TEST_PAYMENT_NOT_ALLOWED);
        }
        Duration retryAfter = orderLimiter.tryAcquire(String.valueOf(userId));
        if (!retryAfter.isZero()) {
            throw new RateLimitExceededException(retryAfter);
        }

        Payment payment = paymentRepository.save(Payment.create(
                userId, newOrderId(), product.code(), product.amount(), product.credits(), trimTrailingSlash(origin)));
        return new PaymentOrderResponseDto(
                payment.getOrderId(),
                payment.getAmount(),
                goodsName(product),
                nicepayProperties.clientKey(),
                PAY_METHOD,
                nicepayProperties.returnUrl()
        );
    }

    // 남의 주문은 있는지조차 알려주지 않는다
    @Transactional(readOnly = true)
    public PaymentResponseDto get(Long userId, String orderId) {
        return paymentRepository.findByOrderIdAndUserId(orderId, userId)
                .map(PaymentResponseDto::from)
                .orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // 결제된 주문만 내려준다 (결제하지 않았거나 실패한 주문은 내역이 아니다)
    @Transactional(readOnly = true)
    public PaymentHistoryResponseDto getHistory(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PaymentHistoryResponseDto.from(paymentRepository.findByUserIdAndStatusOrderByIdDesc(
                userId, PaymentStatus.PAID, PageRequest.of(safePage, safeSize)));
    }

    // 테스트 상점 키로 한 결제는 실제 돈이 나가지 않는데 크레딧은 진짜로 지급된다.
    // 누구나 가입할 수 있는 서버에서 이 상태로 열어 두면 분석 · 이미지 생성 비용만 나가므로 운영자만 허용한다
    private boolean isRestrictedTestPayment() {
        return paymentProperties.sandboxAdminOnly() && !nicepayProperties.isProductionKey();
    }

    // 허용 목록에 적힌 주소와 같을 때만 프론트 주소로 본다. 목록이 "*"(전부 허용)면 아무 주소나 결과 화면이 될 수 있으므로 받지 않는다
    private boolean isFrontOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        String allowed = frontOrigins.checkOrigin(origin);
        return allowed != null && !CorsConfiguration.ALL.equals(allowed);
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }

    // 날짜(나이스페이 관리자 화면에서 찾기 쉽게) + 추측할 수 없는 값. 영문 · 숫자 40자로 나이스페이 제한(64자) 안이다.
    // 결제 시도마다 새로 만든다. 망취소가 주문번호로 거래를 찾으므로 주문번호 하나에 결제 시도가 하나뿐이어야 한다
    private String newOrderId() {
        return "RS" + LocalDate.now().format(ORDER_DATE) + UUID.randomUUID().toString().replace("-", "");
    }

    // 결제창과 카드 명세에 보이는 상품명 (나이스페이 제한 40바이트)
    private String goodsName(PaymentProperties.Product product) {
        return String.format(Locale.KOREA, "Resay 크레딧 %,d", product.credits());
    }

    private static String trimTrailingSlash(String origin) {
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }
}
