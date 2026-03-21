package com.vitalis.demo.config;

import com.vitalis.demo.model.User;
import com.vitalis.demo.model.enums.Role;
import org.springframework.stereotype.Component;

@Component
public class SecurityContext {
    private User currentUser;

    public void setLoggedUser(User user) {
        this.currentUser = user;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void logout() {
        this.currentUser = null;
    }

    public boolean isAuthenticated() {
        return currentUser != null;
    }

    // Atalho para verificar se o usuário tem uma permissão específica
    public boolean hasRole(Role role) {
        return isAuthenticated() && currentUser.getUserRole() == role;
    }
}