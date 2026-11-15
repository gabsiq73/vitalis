-- Additive history only. No existing rows, totals or columns are changed.
CREATE TABLE bottle_breakage (
    id uuid PRIMARY KEY,
    create_date timestamp(6) NOT NULL,
    created_by varchar(255),
    last_modified_date timestamp(6) NOT NULL,
    modified_by varchar(255),
    validity varchar(16) NOT NULL CHECK (validity IN ('VALID', 'EXPIRED')),
    quantity integer NOT NULL CHECK (quantity > 0),
    occurred_at timestamp(6) NOT NULL,
    notes varchar(255),
    water_product_id uuid REFERENCES tb_product(prod_id)
);
CREATE INDEX bottle_breakage_occurred_at_idx ON bottle_breakage(occurred_at DESC, create_date DESC);
