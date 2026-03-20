package com.vitalis.demo.infra.exception;

public record ValidationError(String field, String message) {
}
