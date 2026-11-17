CREATE TABLE bottle_purchase (
    id uuid PRIMARY KEY,
    quantity integer NOT NULL CHECK (quantity > 0),
    occurred_at timestamp NOT NULL,
    notes varchar(255),
    water_product_id uuid REFERENCES tb_product(prod_id),
    created_by varchar(255),
    create_date timestamp NOT NULL,
    last_modified_date timestamp NOT NULL,
    modified_by varchar(255)
);
CREATE INDEX idx_bottle_purchase_occurred_at ON bottle_purchase (occurred_at DESC, create_date DESC);
