package com.vitalis.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VITALIS_SCHEMA_TEST_URL", matches = "jdbc:postgresql://.+")
@SpringBootTest(properties = {
        "spring.datasource.url=${VITALIS_SCHEMA_TEST_URL}",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywaySchemaValidationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext context;

    @Test
    void validatesSchemaCreatedByFlywayWithoutLoadingDevRunner() {
        assertThat(jdbc.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '1' and type = 'SQL' and success
                """, Integer.class)).isEqualTo(1);
        assertThat(context.getBeansOfType(TestRunner.class)).isEmpty();
    }
}
