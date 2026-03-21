package com.vitalis.demo.infra.exception;

import java.util.List;

public record UIErrorResponse(
        String title,
        String message,
        List<ValidationError> errors
) {}
