-- Local Docker Compose seed data for reviewers.
-- Enabled by the local-seed Spring profile. StockCounterInitializer copies
-- product_stock.remaining_quantity to Redis stock:{productOptionId} on API startup.

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 1, '제주 오션뷰 10실 한정 초특가 숙박권', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 1);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 2, '부산 해운대 10실 한정 타임딜 숙박권', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 2);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 3, '강릉 파인비치 10실 한정 초특가 숙박권', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 3);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 4, '속초 설악 오션스테이 10실 한정 특가', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 4);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 5, '여수 밤바다 호텔 10실 한정 초특가', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 5);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 6, '경주 한옥스테이 10실 한정 반값딜', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 6);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 7, '전주 한옥마을 10실 한정 초특가 숙박권', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 7);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 8, '남해 오션리조트 10실 한정 선착순 특가', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 8);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 9, '평창 숲속 리조트 10실 한정 힐링 특가', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 9);

INSERT INTO products (id, name, type, created_at, updated_at, deleted_at)
SELECT 10, '인천 공항 스테이 10실 한정 얼리버드 특가', 'BOOKING', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 10);

UPDATE products
SET
    name =
        CASE id
            WHEN 1 THEN '제주 오션뷰 10실 한정 초특가 숙박권'
            WHEN 2 THEN '부산 해운대 10실 한정 타임딜 숙박권'
            WHEN 3 THEN '강릉 파인비치 10실 한정 초특가 숙박권'
            WHEN 4 THEN '속초 설악 오션스테이 10실 한정 특가'
            WHEN 5 THEN '여수 밤바다 호텔 10실 한정 초특가'
            WHEN 6 THEN '경주 한옥스테이 10실 한정 반값딜'
            WHEN 7 THEN '전주 한옥마을 10실 한정 초특가 숙박권'
            WHEN 8 THEN '남해 오션리조트 10실 한정 선착순 특가'
            WHEN 9 THEN '평창 숲속 리조트 10실 한정 힐링 특가'
            WHEN 10 THEN '인천 공항 스테이 10실 한정 얼리버드 특가'
            ELSE name
        END,
    type = 'BOOKING',
    deleted_at = NULL
WHERE id BETWEEN 1 AND 10;

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 1, 1, '오션뷰 더블룸 선착순 10실', 100000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 1);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 2, 2, '해운대 시티뷰 더블룸 선착순 10실', 110000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 2);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 3, 3, '비치프런트 스탠다드 선착순 10실', 120000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 3);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 4, 4, '설악 오션 트윈룸 선착순 10실', 130000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 4);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 5, 5, '밤바다 디럭스룸 선착순 10실', 140000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 5);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 6, 6, '한옥 온돌룸 선착순 10실', 150000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 6);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 7, 7, '한옥마을 스테이 선착순 10실', 160000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 7);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 8, 8, '오션리조트 패밀리룸 선착순 10실', 170000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 8);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 9, 9, '숲속 리조트 트윈룸 선착순 10실', 180000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 9);

INSERT INTO product_options (id, product_id, name, price, sale_open_at, created_at, updated_at, deleted_at)
SELECT 10, 10, '공항 스탠다드룸 선착순 10실', 90000.00, '2000-01-01 00:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_options WHERE id = 10);

UPDATE product_options
SET
    name =
        CASE id
            WHEN 1 THEN '오션뷰 더블룸 선착순 10실'
            WHEN 2 THEN '해운대 시티뷰 더블룸 선착순 10실'
            WHEN 3 THEN '비치프런트 스탠다드 선착순 10실'
            WHEN 4 THEN '설악 오션 트윈룸 선착순 10실'
            WHEN 5 THEN '밤바다 디럭스룸 선착순 10실'
            WHEN 6 THEN '한옥 온돌룸 선착순 10실'
            WHEN 7 THEN '한옥마을 스테이 선착순 10실'
            WHEN 8 THEN '오션리조트 패밀리룸 선착순 10실'
            WHEN 9 THEN '숲속 리조트 트윈룸 선착순 10실'
            WHEN 10 THEN '공항 스탠다드룸 선착순 10실'
            ELSE name
        END,
    price =
        CASE id
            WHEN 1 THEN 100000.00
            WHEN 2 THEN 110000.00
            WHEN 3 THEN 120000.00
            WHEN 4 THEN 130000.00
            WHEN 5 THEN 140000.00
            WHEN 6 THEN 150000.00
            WHEN 7 THEN 160000.00
            WHEN 8 THEN 170000.00
            WHEN 9 THEN 180000.00
            WHEN 10 THEN 90000.00
            ELSE price
        END,
    sale_open_at = '2000-01-01 00:00:00.000',
    deleted_at = NULL
WHERE id BETWEEN 1 AND 10;

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 1, '2030-01-01 15:00:00.000', '2030-01-02 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 1);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 2, '2030-01-02 15:00:00.000', '2030-01-03 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 2);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 3, '2030-01-03 15:00:00.000', '2030-01-04 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 3);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 4, '2030-01-04 15:00:00.000', '2030-01-05 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 4);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 5, '2030-01-05 15:00:00.000', '2030-01-06 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 5);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 6, '2030-01-06 15:00:00.000', '2030-01-07 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 6);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 7, '2030-01-07 15:00:00.000', '2030-01-08 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 7);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 8, '2030-01-08 15:00:00.000', '2030-01-09 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 8);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 9, '2030-01-09 15:00:00.000', '2030-01-10 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 9);

INSERT INTO booking_schedules (product_option_id, check_in_at, check_out_at, created_at, updated_at, deleted_at)
SELECT 10, '2030-01-10 15:00:00.000', '2030-01-11 11:00:00.000', '2026-05-01 00:00:00.000', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking_schedules WHERE product_option_id = 10);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 1, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 1);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 2, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 2);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 3, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 3);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 4, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 4);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 5, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 5);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 6, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 6);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 7, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 7);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 8, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 8);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 9, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 9);

INSERT INTO product_stock (product_option_id, total_quantity, remaining_quantity, updated_at)
SELECT 10, 10, 10, '2026-05-01 00:00:00.000'
WHERE NOT EXISTS (SELECT 1 FROM product_stock WHERE product_option_id = 10);

UPDATE product_stock
SET
    total_quantity = 10,
    remaining_quantity = 10,
    updated_at = '2026-05-01 00:00:00.000'
WHERE product_option_id BETWEEN 1 AND 10;

INSERT INTO users (id, name, created_at, updated_at, deleted_at)
WITH RECURSIVE reviewer_users(id) AS (
    SELECT 1
    UNION ALL
    SELECT id + 1 FROM reviewer_users WHERE id < 1000
)
SELECT
    id,
    CONCAT('reviewer-user-', id),
    '2026-05-01 00:00:00.000',
    NULL,
    NULL
FROM reviewer_users
WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = reviewer_users.id);

UPDATE users
SET
    name = CONCAT('reviewer-user-', id),
    deleted_at = NULL
WHERE id BETWEEN 1 AND 1000;

INSERT INTO user_points (user_id, point_balance, updated_at)
WITH RECURSIVE reviewer_user_points(user_id) AS (
    SELECT 1
    UNION ALL
    SELECT user_id + 1 FROM reviewer_user_points WHERE user_id < 1000
)
SELECT
    user_id,
    1000000,
    '2026-05-01 00:00:00.000'
FROM reviewer_user_points
WHERE NOT EXISTS (SELECT 1 FROM user_points WHERE user_points.user_id = reviewer_user_points.user_id);

UPDATE user_points
SET
    point_balance = 1000000,
    updated_at = '2026-05-01 00:00:00.000'
WHERE user_id BETWEEN 1 AND 1000;
