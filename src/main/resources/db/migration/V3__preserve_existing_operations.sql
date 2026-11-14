-- Compatibility with operations already present on main, after the immutable V1.
-- IF NOT EXISTS also supports databases previously managed by Hibernate update.
ALTER TABLE tb_order ADD COLUMN IF NOT EXISTS ord_manual_debt_amount numeric(10,2);
ALTER TABLE tb_gas_settlement ADD COLUMN IF NOT EXISTS gas_sup_payment_method varchar(255);
ALTER TABLE tb_order_item ADD COLUMN IF NOT EXISTS bottle_mode varchar(255);
ALTER TABLE tb_order_item ADD COLUMN IF NOT EXISTS bottle_stock_applied boolean NOT NULL DEFAULT false;
ALTER TABLE tb_order_item ADD COLUMN IF NOT EXISTS bottle_inventory_tracked boolean NOT NULL DEFAULT false;
ALTER TABLE tb_order_item ADD COLUMN IF NOT EXISTS promotion_water_value numeric(10,2);
CREATE TABLE IF NOT EXISTS bottle_inventory (
 id bigint PRIMARY KEY, create_date timestamp(6) NOT NULL, created_by varchar(255),
 last_modified_date timestamp(6) NOT NULL, modified_by varchar(255),
 valid_quantity integer NOT NULL, expired_quantity integer NOT NULL, configured boolean NOT NULL
);
