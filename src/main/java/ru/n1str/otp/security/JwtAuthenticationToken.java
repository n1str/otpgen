package ru.n1str.otp.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

//Токен аутентификации для JWT.

@Getter
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private final String credentials;

    //Создает неаутентифицированный токен с JWT в качестве учетных данных.

    public JwtAuthenticationToken(String token) {
        super(null);
        this.principal = null;
        this.credentials = token;
        setAuthenticated(false);
    }

    //Создает аутентифицированный токен с деталями пользователя и правами.

    public JwtAuthenticationToken(
            Object principal,
            String token,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.principal = principal;
        this.credentials = token;
        setAuthenticated(true);
    }

    //Получает имя пользователя.

    @Override
    public String getName() {
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return principal != null ? principal.toString() : null;
    }
} 