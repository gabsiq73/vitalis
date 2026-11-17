package com.vitalis.demo.controller;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named = "VITALIS_DELIVERY_TEST_POSTGRES", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55440/vitalis",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres", "spring.datasource.password=delivery_test_only",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "logging.file.name=target/delivery-changes-postgres.log"
})
class DeliverySearchPaymentCorrectionPostgresTest extends DeliverySearchPaymentCorrectionTest {}
