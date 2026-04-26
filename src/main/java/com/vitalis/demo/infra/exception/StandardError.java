package com.vitalis.demo.infra.exception;

import java.time.Instant;
import java.time.LocalDateTime;

public record StandardError(
        LocalDateTime timestamp,
        Integer status,
        String error,
        String message,
        String path
) {}