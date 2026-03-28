package com.vitalis.demo.infra.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.ObjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class ResourceExceptionHandler {

    // 1. Para erros de Validação
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

        List<ValidationErrorResponse.FieldMessage> list = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> new ValidationErrorResponse.FieldMessage(f.getField(), f.getDefaultMessage()))
                .toList();

        ValidationErrorResponse err = new ValidationErrorResponse(
                LocalDateTime.now(),
                status.value(),
                "Erro de validação",
                list
        );

        return ResponseEntity.status(status).body(err);
    }

    // 2. Para erros comuns
    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<StandardError> objectNotFound(ObjectNotFoundException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND; // Erro 404

        StandardError err = new StandardError(
                Instant.now(),
                status.value(),
                "Não encontrado",
                e.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(err);
    }
}