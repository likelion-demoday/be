package com.example.resay.global.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.metamodel.EntityType;
import java.sql.Types;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// 운영 DB는 Flyway 마이그레이션(db/migration)으로만 만들어지고, 서버는 ddl-auto=validate로 뜬다.
// 엔티티를 바꾸고 마이그레이션을 빠뜨리면 운영 배포에서 서버가 뜨지 않으므로 빌드에서 먼저 잡는다.
// 실제 MySQL로 확인하려면 FLYWAY_TEST_DB_URL에 빈 DB 주소, FLYWAY_TEST_DB_PLATFORM=org.hibernate.dialect.MySQLDialect,
// SPRING_DATASOURCE_USERNAME · PASSWORD를 주고 이 테스트만 실행한다 (cleanTest 포함)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 다른 테스트가 쓰는 DB(Hibernate가 만든 테이블)와 섞이지 않게 빈 DB를 따로 쓴다
        "spring.datasource.url=${FLYWAY_TEST_DB_URL:jdbc:h2:mem:flyway;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}",
        // 드라이버는 주소로 고른다 (테스트 전체를 MySQL로 돌릴 때 지정한 MySQL 드라이버가 H2 주소에 쓰이지 않게)
        "spring.datasource.driver-class-name=",
        "spring.jpa.database-platform=${FLYWAY_TEST_DB_PLATFORM:com.example.resay.global.config.FlywayMigrationTest$H2WithMySqlTextDialect}"
})
class FlywayMigrationTest {

    // H2의 MySQL 모드는 longtext를 varchar로 만들어서, @Lob(clob)으로 매핑한 컬럼을 validate가 다른 타입으로 본다.
    // shortcut: H2에서는 문자열 · 대용량 텍스트 타입 차이를 잡지 못한다. 타입까지 보려면 MySQL로 돌린다
    public static class H2WithMySqlTextDialect extends H2Dialect {

        public H2WithMySqlTextDialect(DialectResolutionInfo info) {
            super(info);
        }

        @Override
        public boolean equivalentTypes(int typeCode1, int typeCode2) {
            return super.equivalentTypes(typeCode1, typeCode2)
                    || (isText(typeCode1) && isText(typeCode2));
        }

        private static boolean isText(int typeCode) {
            return typeCode == Types.VARCHAR || typeCode == Types.CLOB;
        }
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 테이블 · 컬럼이 엔티티와 다르면 validate에서 컨텍스트가 뜨지 않아 이 테스트가 실패한다.
    // validate는 유니크 제약을 보지 않는데 크레딧 · 결제의 중복 방지가 유니크 제약에 달려 있어서 따로 확인한다
    @Test
    void migrationsCreateEveryUniqueConstraintOfEntities() {
        Set<String> declared = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .map(type -> type.getAnnotation(Table.class))
                .filter(Objects::nonNull)
                .flatMap(table -> Arrays.stream(table.uniqueConstraints()))
                .map(UniqueConstraint::name)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        Set<String> created = jdbcTemplate.queryForList("""
                        select constraint_name from information_schema.table_constraints
                        where constraint_type = 'UNIQUE' and table_schema = schema()
                        """, String.class).stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        assertThat(declared).contains("uk_credit_ledger_unique_key", "uk_payments_order_id");
        assertThat(created).containsAll(declared);
    }
}
