CREATE TABLE order_products
(
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT      NOT NULL,
    product_id        BIGINT      NOT NULL,
    product_option_id BIGINT      NOT NULL,
    ordered_at        DATETIME(3) NOT NULL,
    confirmed_at      DATETIME(3),
    canceled_at       DATETIME(3),
    INDEX idx_order_products_order_id (order_id),
    INDEX idx_order_products_product_id (product_id),
    INDEX idx_order_products_product_option_id (product_option_id)
) ENGINE = InnoDB;

INSERT INTO order_products (
    order_id,
    product_id,
    product_option_id,
    ordered_at,
    confirmed_at,
    canceled_at
)
SELECT
    o.id,
    po.product_id,
    o.product_option_id,
    o.created_at,
    CASE
        WHEN o.status = 'CONFIRMED' THEN COALESCE(o.updated_at, o.created_at)
        ELSE NULL
    END,
    CASE
        WHEN o.status IN ('FAILED', 'CANCELLED') THEN COALESCE(o.updated_at, o.created_at)
        ELSE NULL
    END
FROM orders o
JOIN product_options po ON po.id = o.product_option_id;

ALTER TABLE orders
    DROP COLUMN product_option_id;
