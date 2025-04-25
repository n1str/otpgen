package ru.n1str.otp.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import ru.n1str.otp.security.JwtAuthenticationFilter;
import ru.n1str.otp.security.JwtAuthenticationProvider;
import ru.n1str.otp.service.AuthUserService;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class AuthUserConfiguration {

    private final AuthUserService userService;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthenticationProvider jwtAuthProvider;


    //Провайдер аутентификации для логина и пароля

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }


    //Конфигурация безопасности для API

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Только логин и регистрация доступны без JWT
                .requestMatchers("/api/jwt/auth").permitAll()
                .requestMatchers("/api/auth/register").permitAll()

                // Административные эндпоинты
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")

                // Все остальные запросы требуют аутентификации
                .anyRequest().authenticated())
            .authenticationProvider(jwtAuthProvider)
            .authenticationProvider(daoAuthenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .exceptionHandling(exc -> {
                exc.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Необходима JWT аутентификация\"}");
                });
                exc.accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Доступ запрещен\"}");
                });
            });
            
        return http.build();
    }

    //Предоставляет AuthenticationManager для JWT аутентификации

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    //Все пароли в базе хранятся не в чистом виде, а в виде bcrypt

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
