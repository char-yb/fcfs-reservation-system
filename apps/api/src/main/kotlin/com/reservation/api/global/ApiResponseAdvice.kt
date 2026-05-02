package com.reservation.api.global

import com.reservation.api.response.ApiResponse
import com.reservation.api.response.ErrorResponse
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

@RestControllerAdvice(basePackages = ["com.reservation.api"])
class ApiResponseAdvice : ResponseBodyAdvice<Any> {
    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        val servletResponse = (response as? ServletServerHttpResponse)?.servletResponse ?: return body
        val status = servletResponse.status
        val resolve = HttpStatus.resolve(status)

        if (request.uri.path == "/actuator/prometheus") {
            return body
        }

        if (body is ApiResponse<*> || body is ErrorResponse) {
            return body
        }

        return when {
            resolve == null || body == null || body is String -> body
            resolve.is2xxSuccessful -> ApiResponse.success(status, body)
            else -> body
        }
    }
}
