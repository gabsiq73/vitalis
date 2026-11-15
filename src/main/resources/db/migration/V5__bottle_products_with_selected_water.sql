ALTER TABLE tb_product DROP CONSTRAINT tb_product_prod_type_check;
ALTER TABLE tb_product ADD CONSTRAINT tb_product_prod_type_check
 CHECK (prod_type IN ('WATER','GAS','FULL_BOTTLE','EXPIRED_BOTTLE'));
ALTER TABLE tb_order_item ADD COLUMN water_product_id uuid;
ALTER TABLE tb_order_item ADD CONSTRAINT order_item_water_product_fk
 FOREIGN KEY (water_product_id) REFERENCES tb_product(prod_id);
