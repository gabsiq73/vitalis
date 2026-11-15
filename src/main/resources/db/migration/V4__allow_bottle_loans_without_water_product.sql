-- Loans track physical bottles, independently from the water brand/product.
-- Keep legacy product references and the existing FK; only relax nullability.
ALTER TABLE tb_loaned_bottle ALTER COLUMN prod_id DROP NOT NULL;
