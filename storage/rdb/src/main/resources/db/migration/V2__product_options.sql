ALTER TABLE products
    ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'BOOKING';

CREATE TABLE product_options
(
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    name         VARCHAR(200) NOT NULL,
    price        BIGINT       NOT NULL,
    sale_open_at DATETIME(3)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3),
    deleted_at   DATETIME(3),
    INDEX idx_product_options_product_id (product_id)
) ENGINE = InnoDB;

CREATE TABLE booking_schedules
(
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_option_id BIGINT      NOT NULL,
    check_in_at       DATETIME(3) NOT NULL,
    check_out_at      DATETIME(3) NOT NULL,
    created_at        DATETIME(3) NOT NULL,
    updated_at        DATETIME(3),
    deleted_at        DATETIME(3),
    CONSTRAINT uk_booking_schedules_product_option_id UNIQUE (product_option_id)
) ENGINE = InnoDB;

INSERT INTO product_options (
    id,
    product_id,
    name,
    price,
    sale_open_at,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    id,
    id,
    name,
    price,
    sale_open_at,
    created_at,
    updated_at,
    deleted_at
FROM products;

INSERT INTO booking_schedules (
    product_option_id,
    check_in_at,
    check_out_at,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    id,
    check_in_at,
    check_out_at,
    created_at,
    updated_at,
    deleted_at
FROM products;

ALTER TABLE product_stock
    ADD COLUMN product_option_id BIGINT;

UPDATE product_stock
SET product_option_id = product_id;

ALTER TABLE product_stock
    DROP PRIMARY KEY;

ALTER TABLE product_stock
    MODIFY COLUMN product_option_id BIGINT NOT NULL;

ALTER TABLE product_stock
    DROP COLUMN product_id;

ALTER TABLE product_stock
    ADD PRIMARY KEY (product_option_id);

ALTER TABLE orders
    ADD COLUMN product_option_id BIGINT;

UPDATE orders
SET product_option_id = product_id;

ALTER TABLE orders
    MODIFY COLUMN product_option_id BIGINT NOT NULL;

ALTER TABLE orders
    DROP COLUMN product_id;

ALTER TABLE products
    DROP COLUMN price;

ALTER TABLE products
    DROP COLUMN check_in_at;

ALTER TABLE products
    DROP COLUMN check_out_at;

ALTER TABLE products
    DROP COLUMN sale_open_at;
