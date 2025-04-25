package ru.n1str.otp.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import ru.n1str.otp.service.JwtService;

//Аутентификация для JWT токенов

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationProvider implements AuthenticationProvider {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;


    //Аутентифицирует пользователя по JWT токену

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
        String token = jwtAuth.getCredentials();
        
        log.debug("Попытка аутентификации с JWT токеном");
        
        try {
            String username = jwtService.extractUsername(token);
            log.debug("Извлечено имя пользователя из токена: {}", username);

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(token, userDetails)) {
                log.debug("JWT токен действителен для пользователя: {}", username);

                JwtAuthenticationToken authToken = new JwtAuthenticationToken(
                        userDetails,
                        token,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(jwtAuth.getDetails());
                
                return authToken;
            } else {
                log.warn("Невалидный JWT токен для пользователя: {}", username);
                throw new BadCredentialsException("Невалидный JWT токен");
            }
        } catch (UsernameNotFoundException e) {
            log.warn("Пользователь из JWT токена не найден: {}", e.getMessage());
            throw new BadCredentialsException("Пользователь не найден: " + e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка аутентификации с JWT: {}", e.getMessage(), e);
            throw new BadCredentialsException("Ошибка JWT аутентификации: " + e.getMessage());
        }
    }

    //Определяет, поддерживает ли провайдер данный тип аутентификации

    @Override
    public boolean supports(Class<?> authentication) {
        return JwtAuthenticationToken.class.isAssignableFrom(authentication);
    }
} 