package com.vitalis.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in, disposable PostgreSQL 16 only. Never reads operational data. */
@EnabledIfSystemProperty(named="closing.postgres", matches="true")
class ClosingMigrationPostgresTest {
    static final String URL="jdbc:postgresql://127.0.0.1:55439/vitalis";
    static final String PASSWORD="closing_test_only";
    @Test void migratesOldDataWithoutChangingAnyOriginalColumn() throws Exception {
        verifyUpgrade(false);
    }
    @Test void baselinesExistingSchemaWithoutRecreatingTablesOrChangingOldData() throws Exception {
        verifyUpgrade(true);
    }
    private void verifyUpgrade(boolean existingSchema) throws Exception {
        String database="closing_migration_"+UUID.randomUUID().toString().replace("-", "");
        try(Connection admin=DriverManager.getConnection(URL,"postgres",PASSWORD); Statement s=admin.createStatement()) { s.executeUpdate("CREATE DATABASE "+database); }
        String databaseUrl="jdbc:postgresql://127.0.0.1:55439/"+database;
        if (existingSchema) {
            try (Connection c=DriverManager.getConnection(databaseUrl,"postgres",PASSWORD); Statement s=c.createStatement()) {
                s.execute(Files.readString(Path.of("src/main/resources/db/migration/V1__baseline.sql")));
            }
            assertThatThrownBy(() -> Flyway.configure().dataSource(databaseUrl,"postgres",PASSWORD).load().migrate())
                    .hasMessageContaining("non-empty schema");
        } else Flyway.configure().dataSource(databaseUrl,"postgres",PASSWORD).target("1").load().migrate();
        try (Connection c=DriverManager.getConnection(databaseUrl,"postgres",PASSWORD); Statement s=c.createStatement()) {
            String audit="create_date,last_modified_date";
            String cli="00000000-0000-0000-0000-000000000001", prod="00000000-0000-0000-0000-000000000002", ord="00000000-0000-0000-0000-000000000003";
            s.executeUpdate("INSERT INTO tb_client(cli_id,cli_name,cli_type,cli_status,cli_is_active,balance,"+audit+") VALUES ('"+cli+"','Cliente antigo','RETAIL','PAID',true,7,'2020-01-01','2020-01-02')");
            s.executeUpdate("INSERT INTO tb_product(prod_id,prod_name,prod_type,prod_base_price,prod_last_cost_price,prod_is_active,"+audit+") VALUES ('"+prod+"','Água antiga','WATER',9,5,true,'2020-01-01','2020-01-02')");
            s.executeUpdate("INSERT INTO tb_order(ord_id,cli_id,ord_status,ord_payment_status,ord_notes,"+audit+") VALUES ('"+ord+"','"+cli+"','DELIVERED','PARTIAL','Histórico preservado','2020-01-01','2020-01-02')");
            s.executeUpdate("INSERT INTO tb_order_item(ord_item_id,ord_id,prod_id,ord_item_quantity,ord_item_unit_price,"+audit+") VALUES ('00000000-0000-0000-0000-000000000004','"+ord+"','"+prod+"',2,9,'2020-01-01','2020-01-02')");
            s.executeUpdate("INSERT INTO tb_payment(pay_id,ord_id,pay_date,pay_amount,pay_method,pay_automatic_gas,"+audit+") VALUES ('00000000-0000-0000-0000-000000000005','"+ord+"','2020-01-02',11,'PIX',false,'2020-01-01','2020-01-02')");
            s.executeUpdate("INSERT INTO tb_cash_movement(id,amount,type,note,occurred_at,"+audit+") VALUES ('00000000-0000-0000-0000-000000000006',12,'WITHDRAWAL','Retirada antiga','2020-01-02','2020-01-01','2020-01-02')");
            s.executeUpdate("INSERT INTO tb_client_credit_entry(id,client_cli_id,amount,method,date,"+audit+") VALUES ('00000000-0000-0000-0000-000000000007','"+cli+"',7,'DINHEIRO','2020-01-02','2020-01-01','2020-01-02')");
            s.executeUpdate("INSERT INTO tb_loaned_bottle(lb_id,cli_id,prod_id,lb_qtd,lb_status,lb_loan_date,"+audit+") VALUES ('00000000-0000-0000-0000-000000000008','"+cli+"','"+prod+"',2,'LOANED','2020-01-02','2020-01-01','2020-01-02')");
            Map<String,List<String>> columns=new TreeMap<>();
            try(ResultSet rs=s.executeQuery("select table_name,column_name from information_schema.columns where table_schema='public' and table_name <> 'flyway_schema_history' order by table_name,ordinal_position")) {
                while(rs.next()) columns.computeIfAbsent(rs.getString(1),ignored->new ArrayList<>()).add(rs.getString(2));
            }
            var before=snapshot(c,columns);
            Path dump=Path.of(existingSchema ? "target/closing-existing-before-baseline.dump" : "target/closing-before-v2.dump");
            Process process=new ProcessBuilder("docker","exec","vitalis-fechamento-pg16","pg_dump","-U","postgres","-Fc",database).redirectOutput(dump.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            assertThat(process.waitFor()).isZero(); assertThat(Files.size(dump)).isPositive();
            if (existingSchema) Flyway.configure().dataSource(databaseUrl,"postgres",PASSWORD).baselineVersion("1").load().baseline();
            Flyway.configure().dataSource(databaseUrl,"postgres",PASSWORD).load().migrate();
            assertThat(snapshot(c,columns)).isEqualTo(before);
            s.executeUpdate("INSERT INTO tb_loaned_bottle(lb_id,cli_id,lb_qtd,lb_status,lb_loan_date,"+audit+") VALUES ('00000000-0000-0000-0000-000000000009','"+cli+"',1,'LOANED','2020-01-02','2020-01-01','2020-01-02')");
            try(ResultSet rs=s.executeQuery("select prod_id from tb_loaned_bottle where lb_id='00000000-0000-0000-0000-000000000009'")) { rs.next(); assertThat(rs.getObject(1)).isNull(); }
            s.executeUpdate("INSERT INTO tb_product(prod_id,prod_name,prod_type,prod_base_price,prod_is_active,"+audit+") VALUES ('00000000-0000-0000-0000-000000000010','Garrafão completo','FULL_BOTTLE',40,true,'2020-01-01','2020-01-02'),('00000000-0000-0000-0000-000000000011','Vencido','EXPIRED_BOTTLE',3,true,'2020-01-01','2020-01-02')");
            s.executeUpdate("INSERT INTO tb_order_item(ord_item_id,ord_id,prod_id,water_product_id,ord_item_quantity,ord_item_unit_price,"+audit+") VALUES ('00000000-0000-0000-0000-000000000012','"+ord+"','00000000-0000-0000-0000-000000000010','"+prod+"',1,40,'2020-01-01','2020-01-02')");
            try(ResultSet rs=s.executeQuery("select unit_cost,cost_estimated from tb_order_item")) { rs.next(); assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("5"); assertThat(rs.getBoolean(2)).isTrue(); }
            try(ResultSet rs=s.executeQuery("select withdrawal_kind,payment_method from tb_cash_movement")) { rs.next(); assertThat(rs.getString(1)).isEqualTo("RETIRADA"); assertThat(rs.getString(2)).isEqualTo("DINHEIRO"); }
            if (existingSchema) {
                try(ResultSet rs=s.executeQuery("select type from flyway_schema_history where version='1'")) { rs.next(); assertThat(rs.getString(1)).isEqualTo("BASELINE"); }
            }
            Files.writeString(Path.of("target/closing-migration-verification.txt"),"PostgreSQL 16.3: baseline/V1 -> V2 -> V3 -> V4 -> V5. Original columns identical in all "+columns.size()+" tables; 8 seeded rows preserved, including legacy loan product. Dump: "+dump+". New loan without product, bottle SKU types, selected water FK, cost backfill and default cash classification verified.\n");
        }
    }
    Map<String,List<String>> snapshot(Connection c,Map<String,List<String>> columns) throws SQLException {
        Map<String,List<String>> result=new TreeMap<>();
        for(var entry:columns.entrySet()) {
            List<String> rows=new ArrayList<>();
            try(Statement s=c.createStatement(); ResultSet rs=s.executeQuery("select row_to_json(original)::text from (select "+String.join(",",entry.getValue())+" from "+entry.getKey()+") original")) { while(rs.next()) rows.add(rs.getString(1)); }
            Collections.sort(rows); result.put(entry.getKey(),rows);
        }
        return result;
    }
}
