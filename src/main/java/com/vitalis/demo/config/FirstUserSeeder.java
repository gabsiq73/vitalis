package com.vitalis.demo.config;

import com.vitalis.demo.model.User;
import com.vitalis.demo.model.enums.Role;
import com.vitalis.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class FirstUserSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 1. Verifica se já existe algum usuário no banco
        if (userRepository.count() == 0) {
            System.out.println("Iniciando a criação do primeiro usuário Admin...");

            User admin = new User();
            admin.setFirstName("Admin");
            admin.setLastName("Vitalis");
            admin.setUsername("admin");
            admin.setEmail("admin@vitalis.com");
            admin.setUserRole(Role.ADMIN);

            // 2. CRIPTOGRAFA A SENHA ANTES DE SALVAR
            String hash = passwordEncoder.encode("admin123");
            admin.setPassword(hash);

            userRepository.save(admin);

            System.out.println("Usuário 'admin' criado com a senha 'admin123'");
        }
    }
}
