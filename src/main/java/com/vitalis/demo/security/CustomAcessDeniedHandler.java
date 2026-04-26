package com.vitalis.demo.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class CustomAcessDeniedHandler implements AccessDeniedHandler {


    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String body = """
                {
                    "timestamp": "%s",
                    "status": 403,
                    "error": "Acesso negado",
                    "message": "Você não tem permissão para acessar este recurso",
                    "path": "%s"
                }
                """.formatted(Instant.now(), request.getRequestURI());

        response.getWriter().write(body);
    }
}
