package com.example.resay.domain.credit.config;

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

class CreditPropertiesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final CreditProperties.Price PRICE = new CreditProperties.Price(1500, 2000, 500);

    // 테스트는 src/test/resources 의 설정으로 돌기 때문에, 배포되는 파일을 직접 읽어 값을 확인한다
    @Test
    void deployedSettingsFollowPricingPolicy() throws IOException {
        CreditProperties properties = bind("src/main/resources/application.yml");

        assertThat(properties.price()).isEqualTo(PRICE);
        // 정상 생성 중인 건을 환급하지 않도록 이미지 한 장의 최대 대기(OpenAI 응답 180초)보다 충분히 길어야 한다
        assertThat(properties.characterStallTimeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(VALIDATOR.validate(properties)).isEmpty();
    }

    // 0이나 음수면 방금 요청한 캐릭터 생성까지 멈춘 것으로 보고 환급한다
    @Test
    void rejectsZeroOrNegativeCharacterStallTimeout() {
        assertThat(VALIDATOR.validate(new CreditProperties(PRICE, Duration.ZERO)))
                .extracting(violation -> violation.getMessage())
                .containsExactly("credit.character-stall-timeout은 0보다 커야 합니다.");
        assertThat(VALIDATOR.validate(new CreditProperties(PRICE, Duration.ofMinutes(-1)))).isNotEmpty();
        assertThat(VALIDATOR.validate(new CreditProperties(PRICE, null))).isNotEmpty();
    }

    private static CreditProperties bind(String path) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("deployed", new FileSystemResource(path));
        return new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources))
                .bind("credit", CreditProperties.class).get();
    }
}
