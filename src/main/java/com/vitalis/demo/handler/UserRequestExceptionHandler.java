package com.vitalis.demo.handler;

import com.vitalis.demo.controller.UserController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice(assignableTypes = UserController.class)
public class UserRequestExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<StandardError> handleInvalidJson(HttpMessageNotReadableException exception,
                                                         HttpServletRequest request) {
        // Responde diretamente, evitando o dispatch autenticado de /error e o log do payload.
        return ResponseEntity.badRequest().body(new StandardError(LocalDateTime.now(), 400,
                "Erro de requisição", "JSON ou perfil de usuário inválido!", request.getRequestURI()));
    }
}
