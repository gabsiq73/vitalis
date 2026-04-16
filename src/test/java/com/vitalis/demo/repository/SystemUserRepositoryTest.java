package com.vitalis.demo.repository;

import com.vitalis.demo.model.SystemUser;
import com.vitalis.demo.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class SystemUserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void shouldSaveAndFindById(){
        SystemUser systemUser = new SystemUser();

        systemUser.setFirstName("Felipe");
        systemUser.setLastName("Levi");
        systemUser.setUsername("felipeLevi");
        systemUser.setEmail("felipe@gmail.com");
        systemUser.setPassword("felipe123");
        systemUser.setUserRole(Role.ADMIN);

        userRepository.save(systemUser);

        if(systemUser.getId() == null){
            throw new RuntimeException("User was not saved");
        }

        Optional<SystemUser> optional = userRepository.findById(systemUser.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("User was not found");
        }

        SystemUser found = optional.get();

        if(!found.getFirstName().equals("Felipe")){
            throw new RuntimeException("Wrong name found!");
        }

        if(!found.getLastName().equals("Levi")){
            throw new RuntimeException("Wrong last name found!");
        }

        if(!found.getEmail().equals("felipe@gmail.com")){
            throw new RuntimeException("Wrong email found!");
        }

        if(!found.getUsername().equals("felipeLevi")){
            throw new RuntimeException("Wrong username found!");
        }

        if(!found.getPassword().equals("felipe123")){
            throw new RuntimeException("Wrong password found!");
        }

        if(found.getUserRole() != Role.ADMIN){
            throw new RuntimeException("Wrong role found");
        }

        System.out.println(found);
    }


}
