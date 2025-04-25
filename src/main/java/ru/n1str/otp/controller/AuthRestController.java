package ru.n1str.otp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.n1str.otp.models.User;
import ru.n1str.otp.repository.UserRepository;
import ru.n1str.otp.service.JwtService;
import ru.n1str.otp.service.UserRoleService;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthRestController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleService userRoleService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;


    //Регистрация нового пользователя с возвращением JWT-токена

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            log.warn("Попытка регистрации с пустым именем пользователя или паролем");
            return ResponseEntity.badRequest().body(Map.of("error", "Имя пользователя и пароль обязательны"));
        }

        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("Попытка регистрации с существующим именем пользователя: {}", username);
            return ResponseEntity.badRequest().body(Map.of("error", "Пользователь с таким именем уже существует"));
        }

        try {
            User newUser = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .build();

            boolean isAdmin = userRoleService.assignRolesForNewUser(newUser);

            User savedUser = userRepository.save(newUser);

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            String token = jwtService.generateToken(authentication);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("id", savedUser.getId());
            response.put("username", savedUser.getUsername());
            response.put("roles", savedUser.getRoleUsers().stream()
                    .map(role -> role.getNameRole())
                    .toList());
            response.put("isAdmin", isAdmin);

            log.info("Успешная регистрация пользователя: {}, isAdmin: {}, токен выдан", username, isAdmin);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при регистрации пользователя: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при регистрации пользователя: " + e.getMessage()));
        }
    }

    //Изменение пароля пользователя

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Authentication authentication, 
            @RequestBody Map<String, String> request) {
        
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");
        
        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Текущий и новый пароли должны быть указаны"
            ));
        }
        
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("Попытка изменения пароля с неверным текущим паролем для пользователя: {}", username);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Текущий пароль неверен"
            ));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("Пароль успешно изменен для пользователя: {}", username);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Пароль успешно изменен"
        ));
    }

    //Получение информации о текущем пользователе

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("roles", user.getRoleUsers().stream()
                .map(role -> role.getNameRole())
                .collect(Collectors.toList()));
        
        return ResponseEntity.ok(profile);
    }

    //Получает информацию о текущем статусе аутентификации пользователя.

    @GetMapping("/status")
    public ResponseEntity<?> getAuthStatus() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
                authentication.getPrincipal().equals("anonymousUser")) {
            log.debug("Запрос статуса аутентификации: не аутентифицирован");
            return ResponseEntity.ok(Map.of(
                "authenticated", false,
                "message", "Пользователь не аутентифицирован"
            ));
        }
        
        String authType = authentication.getClass().getSimpleName();
        
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("username", authentication.getName());
        response.put("authType", authType);
        response.put("roles", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        
        log.debug("Запрос статуса аутентификации: аутентифицирован как {} через {}", 
                authentication.getName(), authType);
        
        return ResponseEntity.ok(response);
    }
} 