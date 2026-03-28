package com.vitalis.demo.infra.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ValidationErrorResponse(
        LocalDateTime timestamp,
        Integer status,
        String error,
        List<FieldMessage> errors
) {
    // Esta classe interna precisa ser pública para o Handler enxergar
    public record FieldMessage(String field, String message) {}
}