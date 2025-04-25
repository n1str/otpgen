package ru.n1str.otp.service;

import lombok.AllArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.n1str.otp.repository.UserRepository;


//Интеграции с системой безопасности Spring.

@Service
@AllArgsConstructor
public class AuthUserService implements UserDetailsService {
    private UserRepository repository;

    //Метод, который ищет пользователя по имени для Spring Security

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByUsername(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPassword())
                        .authorities(user.getRoleUsers()
                                        .stream()
                                        .map(roleUser -> {
                                            String roleName = roleUser.getNameRole();
                                            String authorityName = roleName.startsWith("ROLE_") 
                                                ? roleName 
                                                : "ROLE_" + roleName;
                                            return new SimpleGrantedAuthority(authorityName);
                                        }).toList())
                        .disabled(!user.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не был найден"));
    }
}
