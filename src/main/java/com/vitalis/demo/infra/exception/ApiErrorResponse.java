package com.vitalis.demo.infra.exception;

import java.util.List;

public record ApiErrorResponse(
        int status,
        String message,
        long timestamp,
        List<ValidationError> errors
) {
    public ApiErrorResponse (int status, String message){
        this(status, message, System.currentTimeMillis(), List.of());
    }
}
