package com.reservation.api.exception

import com.reservation.api.response.ApiResponse
import com.reservation.api.response.ErrorResponse
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorLevel
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import com.reservation.support.redis.RedisUnavailableException
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice(basePackages = ["com.reservation.api"])
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log by logger()

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorResponse = ErrorResponse(ex.javaClass.simpleName, ex.message ?: ErrorType.DEFAULT.message)
        val apiResponse = ApiResponse.fail(statusCode.value(), errorResponse)
        return super.handleExceptionInternal(ex, apiResponse, headers, statusCode, request)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        log.warn { "MethodArgumentNotValidException: ${ex.message}" }
        val message =
            ex.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        val errorResponse = ErrorResponse(ErrorType.INVALID_REQUEST.name, message)
        val apiResponse = ApiResponse.fail(status.value(), errorResponse)
        return ResponseEntity.status(status).body(apiResponse)
    }

    override fun handleMissingServletRequestParameter(
        ex: MissingServletRequestParameterException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        log.warn { "MissingServletRequestParameterException: ${ex.message}" }
        val errorResponse =
            ErrorResponse(
                ErrorType.INVALID_REQUEST.name,
                "${ex.parameterName} 파라미터가 필요합니다",
            )
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, errorResponse))
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ApiResponse<ErrorResponse>> {
        log.warn { "MissingRequestHeaderException: ${ex.message}" }
        val errorType =
            when (ex.headerName) {
                "Idempotency-Key" -> ErrorType.IDEMPOTENCY_KEY_MISSING
                else -> ErrorType.INVALID_REQUEST
            }
        val errorResponse = ErrorResponse(errorType.name, errorType.message)
        return ResponseEntity.badRequest().body(ApiResponse.fail(errorType.status, errorResponse))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiResponse<ErrorResponse>> {
        log.warn { "ConstraintViolationException: ${ex.message}" }
        val message = ex.constraintViolations.joinToString(", ") { it.message }
        val errorResponse = ErrorResponse(ErrorType.INVALID_REQUEST.name, message)
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, errorResponse))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<ErrorResponse>> {
        log.warn { "MethodArgumentTypeMismatchException: ${ex.message}" }
        val errorResponse = ErrorResponse(ErrorType.INVALID_REQUEST.name, ErrorType.INVALID_REQUEST.message)
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, errorResponse))
    }

    @ExceptionHandler(ErrorException::class)
    fun handleErrorException(ex: ErrorException): ResponseEntity<ApiResponse<ErrorResponse>> {
        val errorType = ex.errorType
        when (errorType.level) {
            ErrorLevel.INFO -> log.info { "ErrorException: errorType=$errorType" }
            ErrorLevel.WARN -> log.warn { "ErrorException: errorType=$errorType" }
            ErrorLevel.ERROR -> log.error(ex) { "ErrorException: errorType=$errorType" }
        }
        val errorResponse = ErrorResponse(errorType.name, errorType.message)
        return ResponseEntity
            .status(errorType.status)
            .body(ApiResponse.fail(errorType.status, errorResponse))
    }

    @ExceptionHandler(RedisUnavailableException::class)
    fun handleRedisUnavailableException(ex: RedisUnavailableException): ResponseEntity<ApiResponse<ErrorResponse>> {
        val errorType = ErrorType.REDIS_UNAVAILABLE
        log.warn(ex) { "RedisUnavailableException: ${ex.message}" }
        val errorResponse = ErrorResponse(errorType.name, errorType.message)
        return ResponseEntity
            .status(errorType.status)
            .body(ApiResponse.fail(errorType.status, errorResponse))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ApiResponse<ErrorResponse>> {
        log.error(ex) { "RuntimeException: ${ex.message}" }
        val errorResponse = ErrorResponse(ex.javaClass.simpleName, ErrorType.INTERNAL_SERVER_ERROR.message)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(500, errorResponse))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiResponse<ErrorResponse>> {
        log.error(ex) { "Unexpected error: ${ex.message}" }
        val errorResponse = ErrorResponse(ex.javaClass.simpleName, ErrorType.DEFAULT.message)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(500, errorResponse))
    }
}
