-- Additive only: old monetary values, IDs and timestamps remain untouched.
ALTER TABLE tb_cash_movement ADD COLUMN withdrawal_kind varchar(20) NOT NULL DEFAULT 'RETIRADA';
ALTER TABLE tb_cash_movement ADD COLUMN payment_method varchar(20) NOT NULL DEFAULT 'DINHEIRO';
ALTER TABLE tb_cash_movement ADD CONSTRAINT cash_withdrawal_kind_check CHECK (withdrawal_kind IN ('RETIRADA','COMPRA','DESPESA'));
ALTER TABLE tb_cash_movement ADD CONSTRAINT cash_payment_method_check CHECK (payment_method IN ('DINHEIRO','PIX'));
ALTER TABLE tb_order_item ADD COLUMN unit_cost numeric(10,2);
ALTER TABLE tb_order_item ADD COLUMN cost_estimated boolean NOT NULL DEFAULT false;
-- Historical cost cannot be reconstructed reliably. Freeze today's catalog cost.
-- Missing costs stay NULL and are explicitly excluded from profit, with a warning.
UPDATE tb_order_item i SET unit_cost = p.prod_last_cost_price, cost_estimated = true
FROM tb_product p WHERE p.prod_id = i.prod_id;
