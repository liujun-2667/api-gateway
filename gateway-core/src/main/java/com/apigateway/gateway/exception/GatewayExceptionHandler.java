package com.apigateway.gateway.exception;

import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import com.apigateway.common.exception.TooManyRequestsException;
import com.apigateway.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        HttpStatus status;
        String message;
        String retryAfter = null;

        if (ex instanceof UnauthorizedException) {
            status = HttpStatus.UNAUTHORIZED;
            message = ex.getMessage();
        } else if (ex instanceof NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            message = ex.getMessage();
        } else if (ex instanceof TooManyRequestsException) {
            status = HttpStatus.TOO_MANY_REQUESTS;
            message = ex.getMessage();
            Long seconds = ((TooManyRequestsException) ex).getRetryAfterSeconds();
            if (seconds != null) {
                retryAfter = String.valueOf(seconds);
            }
        } else if (ex instanceof BusinessException) {
            status = HttpStatus.BAD_REQUEST;
            message = ex.getMessage();
        } else if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Internal Server Error";
            log.error("Unhandled exception", ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (retryAfter != null) {
            response.getHeaders().set("Retry-After", retryAfter);
        }

        String body = buildErrorBody(status.value(), message);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private String buildErrorBody(int code, String message) {
        return String.format(
            "{\"code\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
            code,
            escapeJson(message),
            LocalDateTime.now().format(FORMATTER)
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
