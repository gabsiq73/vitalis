package com.vitalis.demo.infra.exception;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

import javax.swing.*;

@Component
public class UIExceptionHandler {
    public void handle(Exception e) {
        if (e instanceof BusinessException) {
            showModal("Regra de Negócio", e.getMessage(), JOptionPane.WARNING_MESSAGE);
        }
        else if (e instanceof MethodArgumentNotValidException) {
            // Lógica para extrair erros de validação do Hibernate Validator
            String errors = "Verifique os campos:"; // extrair da exception...
            showModal("Erro de Validação", errors, JOptionPane.ERROR_MESSAGE);
        }
        else {
            e.printStackTrace(); // Log
            showModal("Erro Crítico", "Ocorreu um erro inesperado: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showModal(String title, String message, int type) {
        JOptionPane.showMessageDialog(null, message, title, type);
    }
}
