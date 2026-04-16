package com.vitalis.demo.security;

import com.vitalis.demo.model.SystemUser;
import com.vitalis.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SystemUser user = userService.getByLogin(username);

        if(user == null){
            throw new UsernameNotFoundException("Usuário não encontrado!");
        }

        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getUserRole().name())
                .build();

    }
}
