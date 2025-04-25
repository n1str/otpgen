package ru.n1str.otp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ru.n1str.otp.dao.OtpCodeJdbcDao;
import ru.n1str.otp.models.OtpConfiguration;
import ru.n1str.otp.models.User;
import ru.n1str.otp.models.role.RoleUser;
import ru.n1str.otp.repository.OtpConfigurationRepository;
import ru.n1str.otp.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {
    private final OtpConfigurationRepository configRepo;
    private final UserRepository userRepo;
    private final OtpCodeJdbcDao otpCodeDao;


    //Получение текущей конфигурации OTP (длина кода, время жизни)

    @GetMapping("/otp-config")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<OtpConfiguration> getOtpConfig() {
        log.info("Admin requested OTP configuration");
        validateAdminAccess("получение конфигурации OTP");
        
        OtpConfiguration config = configRepo.findById(1L).orElseGet(() -> {
            OtpConfiguration defaultConfig = new OtpConfiguration();
            defaultConfig.setId(1L);
            defaultConfig.setCodeLength(6);
            defaultConfig.setLifetimeMinutes(5);
            return configRepo.save(defaultConfig);
        });
        return ResponseEntity.ok(config);
    }

    //Обновление конфигурации OTP

    @PutMapping("/otp-config")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<OtpConfiguration> updateOtpConfig(@RequestBody Map<String, Integer> requestBody) {
        log.info("Admin updating OTP configuration");
        validateAdminAccess("обновление конфигурации OTP");
        
        Integer codeLength = requestBody.get("codeLength");
        Integer lifetimeMinutes = requestBody.get("lifetimeMinutes");

        if (codeLength == null || lifetimeMinutes == null) {
            return ResponseEntity.badRequest().build();
        }

        OtpConfiguration config = configRepo.findById(1L).orElseGet(() -> {
            OtpConfiguration defaultConfig = new OtpConfiguration();
            defaultConfig.setId(1L);
            return defaultConfig;
        });

        config.setCodeLength(codeLength);
        config.setLifetimeMinutes(lifetimeMinutes);

        log.info("Admin updated OTP configuration: length={}, lifetime={}min", codeLength, lifetimeMinutes);
        return ResponseEntity.ok(configRepo.save(config));
    }

    //Получение списка всех пользователей кроме администраторов

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        log.info("Admin requested users list");
        validateAdminAccess("получение списка пользователей");
        
        List<User> users = userRepo.findAll().stream()
                .filter(user -> !user.getRoleUsers().stream()
                        .anyMatch(role -> 
                            role.getNameRole().equals("ROLE_ADMIN") || 
                            role.getNameRole().equals("ADMIN")))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = users.stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("roles", user.getRoleUsers().stream()
                    .map(RoleUser::getNameRole)
                    .collect(Collectors.toList()));
            return map;
        }).collect(Collectors.toList());

        log.info("Admin requested user list, found {} non-admin users", users.size());
        return ResponseEntity.ok(result);
    }

    //Удаление пользователя по ID

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        log.info("Admin attempting to delete user {}", id);
        validateAdminAccess("удаление пользователя");
        
        if (!userRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        User user = userRepo.findById(id).get();

        boolean isAdmin = user.getRoleUsers().stream()
                .anyMatch(role -> 
                    role.getNameRole().equals("ROLE_ADMIN") || 
                    role.getNameRole().equals("ADMIN"));

        if (isAdmin) {
            log.warn("Attempted to delete admin user: {}", id);
            return ResponseEntity.badRequest().body("Cannot delete admin users");
        }

        otpCodeDao.deleteByUser(user);

        userRepo.deleteById(id);
        log.info("Deleted user: {} ({})", user.getUsername(), id);

        return ResponseEntity.ok().build();
    }

    //Вспомогательный метод для валидации доступа администратора

    private void validateAdminAccess(String action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || 
            !auth.isAuthenticated() || 
            auth.getAuthorities().stream().noneMatch(a -> 
                a.getAuthority().equals("ROLE_ADMIN") || 
                a.getAuthority().equals("ADMIN"))) {
            
            String username = auth != null ? auth.getName() : "unknown";
            log.warn("Пользователь {} пытается выполнить административное действие '{}' без прав администратора", 
                      username, action);
            
            throw new SecurityException("Доступ запрещен: требуются права администратора");
        }
    }
}