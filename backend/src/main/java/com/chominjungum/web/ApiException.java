package com.chominjungum.web;

import org.springframework.http.HttpStatus;

/** 사용자에게 그대로 보여줄 수 있는 오류. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
