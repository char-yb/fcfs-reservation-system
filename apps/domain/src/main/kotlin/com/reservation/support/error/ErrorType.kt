package com.reservation.support.error

enum class ErrorType(
    val status: Int,
    val kind: ErrorKind,
    val message: String,
    val level: ErrorLevel,
) {
    /** Common */
    DEFAULT(500, ErrorKind.INTERNAL_SERVER_ERROR, "예기치 못한 오류가 발생했습니다.", ErrorLevel.ERROR),
    INVALID_REQUEST(400, ErrorKind.CLIENT_ERROR, "잘못된 요청입니다.", ErrorLevel.WARN),
    METHOD_NOT_ALLOWED(405, ErrorKind.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP method입니다.", ErrorLevel.WARN),
    INTERNAL_SERVER_ERROR(500, ErrorKind.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.", ErrorLevel.ERROR),

    /** Idempotency */
    IDEMPOTENCY_KEY_MISSING(400, ErrorKind.CLIENT_ERROR, "Idempotency-Key 헤더가 필요합니다.", ErrorLevel.WARN),
    IDEMPOTENCY_KEY_INVALID(400, ErrorKind.CLIENT_ERROR, "Idempotency-Key는 UUID v4 형식이어야 합니다.", ErrorLevel.WARN),

    /** Product */
    PRODUCT_NOT_FOUND(404, ErrorKind.NOT_FOUND, "상품을 찾을 수 없습니다.", ErrorLevel.INFO),
    SALE_NOT_OPEN(403, ErrorKind.FORBIDDEN_ERROR, "아직 판매가 시작되지 않았습니다.", ErrorLevel.INFO),

    /** User */
    USER_NOT_FOUND(404, ErrorKind.NOT_FOUND, "사용자를 찾을 수 없습니다.", ErrorLevel.INFO),
    INSUFFICIENT_POINT(422, ErrorKind.CLIENT_ERROR, "포인트 잔액이 부족합니다.", ErrorLevel.INFO),

    /** Stock */
    STOCK_SOLD_OUT(409, ErrorKind.CONFLICT, "재고가 소진되었습니다.", ErrorLevel.INFO),

    /** Order */
    DUPLICATE_REQUEST(409, ErrorKind.CONFLICT, "이미 처리된 요청입니다.", ErrorLevel.INFO),

    /** Payment */
    PAYMENT_METHOD_INVALID(422, ErrorKind.CLIENT_ERROR, "결제 수단 조합이 유효하지 않습니다.", ErrorLevel.WARN),
    PAYMENT_DECLINED(402, ErrorKind.PAYMENT_ERROR, "결제가 거절되었습니다.", ErrorLevel.WARN),

    /** Order */
    INVALID_ORDER_STATUS_TRANSITION(409, ErrorKind.CONFLICT, "유효하지 않은 주문 상태 전환입니다.", ErrorLevel.WARN),

    /** Infrastructure */
    LOCK_ACQUISITION_FAILED(503, ErrorKind.SERVER_ERROR, "일시적으로 처리할 수 없습니다. 잠시 후 다시 시도해주세요.", ErrorLevel.WARN),
}
