package com.vitalis.demo.handler;

import java.time.LocalDateTime;

public record StandardError(
        LocalDateTime timestamp,
        Integer status,
        String error,
        String message,
        String path
) {}