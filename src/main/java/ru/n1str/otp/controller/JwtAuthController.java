package ru.n1str.otp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import ru.n1str.otp.repository.UserRepository;
import ru.n1str.otp.service.JwtService;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jwt")
@RequiredArgsConstructor
@Slf4j
public class JwtAuthController {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    //Создает JWT токен на основе учетных данных пользователя.

    @PostMapping("/auth")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            log.warn("Попытка аутентификации с отсутствующим именем пользователя или паролем");
            return ResponseEntity.badRequest().body(Map.of("error", "Имя пользователя и пароль обязательны"));
        }

        try {
            log.debug("Попытка аутентификации пользователя: {}", username);

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            String token = jwtService.generateToken(authentication);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("username", username);
            response.put("roles", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));

            log.info("Аутентификация успешна для пользователя: {}", username);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            log.warn("Неверные учетные данные для пользователя: {}", username);
            return ResponseEntity.badRequest().body(Map.of("error", "Неверное имя пользователя или пароль"));
        } catch (AuthenticationException e) {
            log.error("Ошибка аутентификации для пользователя: {}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Ошибка аутентификации"));
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при аутентификации пользователя: {}: {}", username, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Произошла непредвиденная ошибка"));
        }
    }
}