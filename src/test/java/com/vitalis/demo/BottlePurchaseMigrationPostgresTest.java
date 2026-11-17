package com.vitalis.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** PostgreSQL 16 descartável, criado explicitamente para esta verificação. */
@EnabledIfSystemProperty(named="purchases.postgres", matches="true")
class BottlePurchaseMigrationPostgresTest {
    @Test void v7PreservesExistingInventoryAndProductsAndCreatesAuditedPurchaseHistory() throws Exception {
        String database="purchase_migration_"+UUID.randomUUID().toString().replace("-", "");
        String adminUrl="jdbc:postgresql://127.0.0.1:55440/postgres";
        try (Connection admin=DriverManager.getConnection(adminUrl,"postgres","operational_test_only"); Statement command=admin.createStatement()) {
            command.executeUpdate("CREATE DATABASE "+database);
            try {
                String url="jdbc:postgresql://127.0.0.1:55440/"+database;
                Flyway.configure().dataSource(url,"postgres","operational_test_only").target("6").load().migrate();
                try (Connection c=DriverManager.getConnection(url,"postgres","operational_test_only"); Statement s=c.createStatement()) {
                    String id="00000000-0000-0000-0000-000000000001";
                    s.executeUpdate("INSERT INTO tb_product(prod_id,prod_name,prod_type,prod_base_price,prod_is_active,create_date,last_modified_date) VALUES ('"+id+"','Água antiga','WATER',10,true,'2020-01-01','2020-01-02')");
                    s.executeUpdate("INSERT INTO bottle_inventory(id,valid_quantity,expired_quantity,configured,create_date,last_modified_date) VALUES (1,15,2,true,'2020-01-01','2020-01-02')");
                    String products=snapshot(s,"tb_product"), inventory=snapshot(s,"bottle_inventory");
                    assertThat(Flyway.configure().dataSource(url,"postgres","operational_test_only").load().migrate().migrationsExecuted).isEqualTo(1);
                    assertThat(snapshot(s,"tb_product")).isEqualTo(products);
                    assertThat(snapshot(s,"bottle_inventory")).isEqualTo(inventory);
                    s.executeUpdate("INSERT INTO bottle_purchase(id,quantity,occurred_at,water_product_id,create_date,last_modified_date,created_by) VALUES ('"+UUID.randomUUID()+"',3,'2026-11-01','"+id+"','2026-11-01','2026-11-01','operador')");
                    try (ResultSet r=s.executeQuery("SELECT quantity,created_by FROM bottle_purchase")) {
                        assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isEqualTo(3); assertThat(r.getString(2)).isEqualTo("operador");
                    }
                    assertThatThrownBy(() -> s.executeUpdate("INSERT INTO bottle_purchase(id,quantity,occurred_at,create_date,last_modified_date) VALUES ('"+UUID.randomUUID()+"',0,now(),now(),now())")).isInstanceOf(SQLException.class);
                }
            } finally { command.executeUpdate("DROP DATABASE "+database+" WITH (FORCE)"); }
        }
    }
    String snapshot(Statement statement, String table) throws SQLException {
        try (ResultSet r=statement.executeQuery("SELECT row_to_json(t)::text FROM "+table+" t")) { r.next(); return r.getString(1); }
    }
}
