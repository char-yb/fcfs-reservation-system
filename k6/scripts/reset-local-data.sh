#!/usr/bin/env bash
set -euo pipefail

project="fcfs-reservation"
mysql_container="${project}-mysql"
redis_container="${project}-redis"
mysql_database="fcfs_reservation"
mysql_user="reservation"
mysql_password="reservation"

product_id=1
product_option_id=1
product_price=100000
stock=10
user_start_id=1
user_count=1000
user_point_balance=1000000

require_positive_int() {
    local name="$1"
    local value="$2"
    if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 1 ]]; then
        echo "$name 값은 양의 정수여야 합니다: $value" >&2
        exit 1
    fi
}

require_container() {
    local name="$1"
    if ! docker ps --format '{{.Names}}' | grep -Fx "$name" >/dev/null; then
        echo "컨테이너가 실행 중이 아닙니다: $name" >&2
        exit 1
    fi
}

require_positive_int PRODUCT_ID "$product_id"
require_positive_int PRODUCT_OPTION_ID "$product_option_id"
require_positive_int PRODUCT_PRICE "$product_price"
require_positive_int STOCK "$stock"
require_positive_int USER_START_ID "$user_start_id"
require_positive_int USER_COUNT "$user_count"
require_positive_int USER_POINT_BALANCE "$user_point_balance"

require_container "$mysql_container"
require_container "$redis_container"

sql_file="$(mktemp)"
trap 'rm -f "$sql_file"' EXIT

cat >"$sql_file" <<SQL
SET SESSION sql_notes = 0;
SET @product_id := ${product_id};
SET @product_option_id := ${product_option_id};
SET @product_price := ${product_price};
SET @stock := ${stock};
SET @user_start_id := ${user_start_id};
SET @user_end_id := ${user_start_id} + ${user_count} - 1;

DELETE p
FROM payments p
JOIN orders o ON o.id = p.order_id
JOIN order_products op ON op.order_id = o.id
WHERE op.product_option_id = @product_option_id;

DELETE FROM outbox_events
WHERE order_id IN (
    SELECT order_id FROM order_products WHERE product_option_id = @product_option_id
);

DELETE o
FROM orders o
JOIN order_products op ON op.order_id = o.id
WHERE op.product_option_id = @product_option_id;

DELETE FROM order_products WHERE product_option_id = @product_option_id;
DELETE FROM product_stock WHERE product_option_id = @product_option_id;
DELETE FROM booking_schedules WHERE product_option_id = @product_option_id;
DELETE FROM product_options WHERE id = @product_option_id;
DELETE FROM products WHERE id = @product_id;
DELETE FROM user_points WHERE user_id BETWEEN @user_start_id AND @user_end_id;
DELETE FROM users WHERE id BETWEEN @user_start_id AND @user_end_id;

INSERT INTO products (
    id,
    name,
    type,
    created_at,
    updated_at,
    deleted_at
) VALUES (
    @product_id,
    'K6 Midnight Room',
    'BOOKING',
    NOW(3),
    NULL,
    NULL
);

INSERT INTO product_options (
    id,
    product_id,
    name,
    price,
    sale_open_at,
    created_at,
    updated_at,
    deleted_at
) VALUES (
    @product_option_id,
    @product_id,
    'Standard',
    @product_price,
    '2000-01-01 00:00:00.000',
    NOW(3),
    NULL,
    NULL
);

INSERT INTO booking_schedules (
    product_option_id,
    check_in_at,
    check_out_at,
    created_at,
    updated_at,
    deleted_at
) VALUES (
    @product_option_id,
    '2030-01-01 15:00:00.000',
    '2030-01-02 11:00:00.000',
    NOW(3),
    NULL,
    NULL
);

INSERT INTO product_stock (
    product_option_id,
    total_quantity,
    remaining_quantity,
    updated_at
) VALUES (
    @product_option_id,
    @stock,
    @stock,
    NOW(3)
);

INSERT INTO users (id, name, created_at, updated_at, deleted_at)
VALUES
SQL

for ((i = 0; i < user_count; i += 1)); do
    user_id=$((user_start_id + i))
    if [[ "$i" -gt 0 ]]; then
        printf ",\n" >>"$sql_file"
    fi
    printf "(%d, 'k6-user-%d', NOW(3), NULL, NULL)" "$user_id" "$user_id" >>"$sql_file"
done

cat >>"$sql_file" <<SQL
;

INSERT INTO user_points (user_id, point_balance, updated_at)
VALUES
SQL

for ((i = 0; i < user_count; i += 1)); do
    user_id=$((user_start_id + i))
    if [[ "$i" -gt 0 ]]; then
        printf ",\n" >>"$sql_file"
    fi
    printf "(%d, %d, NOW(3))" "$user_id" "$user_point_balance" >>"$sql_file"
done

cat >>"$sql_file" <<SQL
;
SQL

docker exec -i "$mysql_container" mysql \
    -u"$mysql_user" \
    -p"$mysql_password" \
    "$mysql_database" <"$sql_file"

docker exec "$redis_container" redis-cli SET "stock:${product_option_id}" "$stock" >/dev/null

echo "테스트 데이터 준비 완료 product_id=${product_id} product_option_id=${product_option_id} stock=${stock} users=${user_start_id}..$((user_start_id + user_count - 1))"
echo "Redis stock:${product_option_id}=$(docker exec "$redis_container" redis-cli GET "stock:${product_option_id}")"
