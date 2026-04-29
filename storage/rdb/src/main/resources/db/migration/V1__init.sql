CREATE TABLE users
(
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3),
    deleted_at DATETIME(3)
) ENGINE = InnoDB;

CREATE TABLE user_points
(
    user_id       BIGINT      NOT NULL PRIMARY KEY,
    point_balance BIGINT      NOT NULL DEFAULT 0,
    updated_at    DATETIME(3) NOT NULL
) ENGINE = InnoDB;

CREATE TABLE products
(
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    price        BIGINT       NOT NULL,
    check_in_at  DATETIME(3)  NOT NULL,
    check_out_at DATETIME(3)  NOT NULL,
    sale_open_at DATETIME(3)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3),
    deleted_at   DATETIME(3)
) ENGINE = InnoDB;

CREATE TABLE product_stock
(
    product_id          BIGINT      NOT NULL PRIMARY KEY,
    total_quantity      INT         NOT NULL,
    remaining_quantity  INT         NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    updated_at          DATETIME(3) NOT NULL,
    CONSTRAINT chk_remaining_non_negative CHECK (remaining_quantity >= 0)
) ENGINE = InnoDB;

CREATE TABLE orders
(
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    total_amount    BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    order_key       VARCHAR(64) NOT NULL,
    created_at      DATETIME(3) NOT NULL,
    updated_at      DATETIME(3),
    deleted_at      DATETIME(3),
    CONSTRAINT uk_order_key UNIQUE (order_key),
    INDEX idx_user_created (user_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE payments
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id            BIGINT       NOT NULL,
    method              VARCHAR(20)  NOT NULL,
    amount              BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    pg_transaction_id   VARCHAR(100),
    external_request_id VARCHAR(100),
    created_at          DATETIME(3)  NOT NULL,
    updated_at          DATETIME(3),
    deleted_at          DATETIME(3),
    INDEX idx_order_id (order_id)
) ENGINE = InnoDB;

CREATE TABLE outbox_events
(
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT      NOT NULL,
    event_type   VARCHAR(30) NOT NULL,
    payload      TEXT        NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   DATETIME(3) NOT NULL,
    processed_at DATETIME(3),
    retry_count  INT         NOT NULL DEFAULT 0,
    INDEX idx_outbox_status_type (status, event_type),
    INDEX idx_outbox_order_id (order_id)
) ENGINE = InnoDB;
