#!/usr/bin/env bash
set -euo pipefail

project="fcfs-reservation"
mysql_container="${project}-mysql"
redis_container="${project}-redis"
mysql_database="fcfs_reservation"
mysql_user="reservation"
mysql_password="reservation"
product_id=1
allow_redis_drift=0

failures=0

fail() {
    echo "실패: $*" >&2
    failures=$((failures + 1))
}

mysql_query() {
    docker exec "$mysql_container" mysql \
        -N \
        -B \
        -u"$mysql_user" \
        -p"$mysql_password" \
        "$mysql_database" \
        -e "$1"
}

if ! docker ps --format '{{.Names}}' | grep -Fx "$mysql_container" >/dev/null; then
    echo "컨테이너가 실행 중이 아닙니다: $mysql_container" >&2
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -Fx "$redis_container" >/dev/null; then
    echo "컨테이너가 실행 중이 아닙니다: $redis_container" >&2
    exit 1
fi

stock_row="$(mysql_query "SELECT total_quantity, remaining_quantity FROM product_stock WHERE product_id = ${product_id};")"
if [[ -z "$stock_row" ]]; then
    echo "product_id=${product_id}에 해당하는 product_stock 행이 없습니다" >&2
    exit 1
fi

read -r total_quantity remaining_quantity <<<"$stock_row"

order_row="$(
    mysql_query "
        SELECT
            COALESCE(SUM(status = 'CONFIRMED'), 0),
            COALESCE(SUM(status = 'FAILED'), 0),
            COALESCE(SUM(status = 'PENDING'), 0),
            COUNT(*)
        FROM orders
        WHERE product_id = ${product_id};
    "
)"
read -r confirmed_orders failed_orders pending_orders total_orders <<<"$order_row"

redis_stock="$(docker exec "$redis_container" redis-cli GET "stock:${product_id}" | tr -d '\r')"
expected_remaining=$((total_quantity - confirmed_orders))

echo "product_id=${product_id}"
echo "total_quantity=${total_quantity}"
echo "confirmed_orders=${confirmed_orders}"
echo "failed_orders=${failed_orders}"
echo "pending_orders=${pending_orders}"
echo "total_orders=${total_orders}"
echo "db_remaining=${remaining_quantity}"
echo "expected_remaining=${expected_remaining}"
echo "redis_stock=${redis_stock:-<missing>}"

if [[ "$confirmed_orders" -gt "$total_quantity" ]]; then
    fail "confirmed_orders(${confirmed_orders})가 total_quantity(${total_quantity})보다 큽니다"
fi

if [[ "$remaining_quantity" -ne "$expected_remaining" ]]; then
    fail "DB remaining(${remaining_quantity})은 total_quantity - confirmed_orders(${expected_remaining})와 같아야 합니다"
fi

if [[ -z "$redis_stock" ]]; then
    fail "Redis stock:${product_id} 키가 없습니다"
elif [[ "$redis_stock" != "$remaining_quantity" ]]; then
    if [[ "$allow_redis_drift" == "1" ]]; then
        echo "경고: Redis stock drift 허용 redis=${redis_stock}, db=${remaining_quantity}" >&2
    else
        fail "Redis stock(${redis_stock})은 DB remaining(${remaining_quantity})과 같아야 합니다"
    fi
fi

if [[ "$pending_orders" -ne 0 ]]; then
    fail "완료된 부하테스트 이후 pending_orders는 0이어야 합니다"
fi

if [[ "$failures" -gt 0 ]]; then
    exit 1
fi

echo "정합성 검증 통과"
