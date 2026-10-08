package com.example.resay.domain.payment.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPropertiesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    // 테스트는 src/test/resources 의 설정으로 돌기 때문에, 실제로 배포되는 가격표는 다른 테스트가 읽지 않는다.
    // 여기서 배포되는 파일을 직접 읽어 오타(금액과 크레딧이 다름, 코드 중복 등)를 잡는다
    @Test
    void deployedProductsFollowChargePolicy() throws IOException {
        PaymentProperties properties = bind("src/main/resources/application.yml");

        assertThat(properties.products()).extracting(PaymentProperties.Product::code)
                .containsExactly("CREDIT_1000", "CREDIT_3000", "CREDIT_5000", "CREDIT_10000");
        assertThat(properties.products()).extracting(PaymentProperties.Product::amount)
                .containsExactly(1000, 3000, 5000, 10000);
        // 1원 = 1크레딧 (보너스 · 할인 없음)
        assertThat(properties.products()).allSatisfy(product ->
                assertThat(product.credits()).isEqualTo(product.amount()));
        assertThat(properties.orderExpiration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.approvalGracePeriod()).isEqualTo(Duration.ofMinutes(3));
        assertThat(VALIDATOR.validate(properties)).isEmpty();
    }

    // 카드 결제는 1,000원 미만이 승인되지 않는다. 그런 상품을 만들면 사용자가 카드 인증까지 하고 매번 거절당한다
    @Test
    void rejectsProductBelowCardMinimum() {
        PaymentProperties properties = new PaymentProperties(
                List.of(new PaymentProperties.Product("CREDIT_500", 500, 500)),
                Duration.ofMinutes(30), Duration.ofMinutes(3), false);

        assertThat(VALIDATOR.validate(properties)).isNotEmpty();
    }

    // 같은 코드가 두 번 있으면 뒤의 상품이 조용히 무시된다
    @Test
    void rejectsDuplicateProductCodes() {
        PaymentProperties properties = new PaymentProperties(
                List.of(new PaymentProperties.Product("CREDIT_1000", 1000, 1000),
                        new PaymentProperties.Product("CREDIT_1000", 3000, 3000)),
                Duration.ofMinutes(30), Duration.ofMinutes(3), false);

        assertThat(VALIDATOR.validate(properties))
                .extracting(violation -> violation.getMessage())
                .containsExactly("payment.products의 code가 중복됩니다.");
    }

    private static PaymentProperties bind(String path) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("deployed", new FileSystemResource(path));
        // 값에 들어 있는 ${환경변수:기본값} 은 기본값으로 푼다
        return new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources))
                .bind("payment", PaymentProperties.class).get();
    }
}
