package com.apigateway.common.exception;

import lombok.Getter;

@Getter
public class TooManyRequestsException extends RuntimeException {

    private final Long retryAfterSeconds;

    public TooManyRequestsException(String message) {
        super(message);
        this.retryAfterSeconds = null;
    }

    public TooManyRequestsException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public TooManyRequestsException(String message, Throwable cause) {
        super(message, cause);
        this.retryAfterSeconds = null;
    }
}
