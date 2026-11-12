-- Schema legado de teste; não é migration e nunca usa o banco operacional.
CREATE TABLE tb_client_fidelity (
    cli_fidelity_id UUID PRIMARY KEY,
    points INTEGER,
    pending_bonus_water INTEGER
);
INSERT INTO tb_client_fidelity VALUES ('00000000-0000-0000-0000-000000000003', 7, 2);
CREATE TABLE tb_client (
    cli_id UUID PRIMARY KEY,
    cli_fidelity_id UUID REFERENCES tb_client_fidelity(cli_fidelity_id)
);
CREATE TABLE tb_system_config (
    id BIGINT PRIMARY KEY,
    points_per_water_item INTEGER NOT NULL,
    points_per_free_water INTEGER NOT NULL,
    pickup_discount_cents INTEGER NOT NULL
);
-- Preparação não destrutiva documentada para os dois campos legados NOT NULL.
ALTER TABLE tb_system_config ALTER COLUMN points_per_water_item SET DEFAULT 1;
ALTER TABLE tb_system_config ALTER COLUMN points_per_free_water SET DEFAULT 10;
