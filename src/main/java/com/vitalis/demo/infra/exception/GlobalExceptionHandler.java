package com.vitalis.demo.infra.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.ObjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Para erros de Validação
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

        List<ValidationErrorResponse.FieldMessage> fields = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> new ValidationErrorResponse.FieldMessage(f.getField(), f.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(status).body(
                new ValidationErrorResponse(LocalDateTime.now(), status.value()
                        , "Erro de validação", fields));
    }

    // 2. Para erros comuns
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<StandardError> handleNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND; // Erro 404

        return ResponseEntity.status(status).body(
                new StandardError(LocalDateTime.now(), status.value()
                        , "Não encontrado", e.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<StandardError> handleBussiness(BusinessException e, HttpServletRequest request){
        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(
                new StandardError(LocalDateTime.now(), status.value()
                        , "Erro de negócio", e.getMessage(), request.getRequestURI())
        );

    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<StandardError> handleOutOfStock(OutOfStockException e, HttpServletRequest request){
        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(
                new StandardError(LocalDateTime.now(), status.value(),
                        "Erro de estoque", e.getMessage(), request.getRequestURI())
        );
    }


}