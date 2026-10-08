package com.example.resay.domain.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param products            충전 상품. 금액은 프론트가 아니라 여기서 정한다
 * @param orderExpiration     결제하지 않은 주문을 만료 처리하기까지의 시간
 * @param approvalGracePeriod 승인을 요청한 주문을 정리 작업이 건드리기 전까지 기다리는 시간.
 *                            승인 · 망취소 요청이 끝나기 전에 끼어들지 않도록 두 요청의 타임아웃 합보다 길어야 한다
 *                            (서버가 뜰 때 PaymentReconciler가 확인한다)
 * @param approvalRetryDelays 승인 요청에 "그런 인증 내역이 없다"는 답이 왔을 때 다시 요청하기 전에 기다리는 시간들.
 *                            인증 결과를 받자마자 승인을 요청하면 나이스페이 쪽에 인증 내역이 아직 보이지 않을 때가 있다
 * @param sandboxAdminOnly    테스트 상점 키로 동작하는 동안 운영자(ADMIN)만 충전할 수 있게 할지.
 *                            테스트 결제는 실제 돈이 나가지 않으므로, 공개된 서버에서는 아무나 크레딧을 얻는 통로가 된다
 */
@Validated
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        @NotEmpty List<@Valid Product> products,
        @NotNull Duration orderExpiration,
        @NotNull Duration approvalGracePeriod,
        List<Duration> approvalRetryDelays,
        boolean sandboxAdminOnly
) {

    public PaymentProperties {
        approvalRetryDelays = approvalRetryDelays == null ? List.of() : List.copyOf(approvalRetryDelays);
    }

    /**
     * @param code    주문에 기록되는 상품 코드
     * @param amount  결제 금액(원). 카드 결제는 1,000원 미만이 승인되지 않는다 (나이스페이 결과 코드 3041)
     * @param credits 지급 크레딧
     */
    public record Product(
            @NotBlank @Size(max = 30) String code,
            @Min(1000) int amount,
            @Min(1) int credits
    ) {
    }

    // 같은 code가 두 번 있으면 뒤의 상품이 조용히 무시된다
    @AssertTrue(message = "payment.products의 code가 중복됩니다.")
    public boolean isProductCodeUnique() {
        return products == null
                || products.stream().map(Product::code).distinct().count() == products.size();
    }

    public Optional<Product> findProduct(String code) {
        return products.stream().filter(product -> product.code().equals(code)).findFirst();
    }
}
