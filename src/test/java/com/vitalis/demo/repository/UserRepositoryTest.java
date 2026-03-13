package com.vitalis.demo.repository;

import com.vitalis.demo.model.User;
import com.vitalis.demo.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void saveClient(){
        User user = new User();

        user.setFirstName("Felipe");
        user.setLastName("Sexo");
        user.setUsername("felipeSexo");
        user.setEmail("felipesexo@gmail.com");
        user.setPassword("sexo123");
        user.setUserRole(Role.ADMINISTRATOR);

        userRepository.save(user);
    }

    @Test

}
