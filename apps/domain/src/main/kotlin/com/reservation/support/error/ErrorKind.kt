package com.reservation.support.error

enum class ErrorKind {
    CLIENT_ERROR,
    SERVER_ERROR,
    INTERNAL_SERVER_ERROR,
    NOT_FOUND,
    FORBIDDEN_ERROR,
    METHOD_NOT_ALLOWED,
    TOO_MANY_REQUESTS,
    CONFLICT,
    PAYMENT_ERROR,
}
