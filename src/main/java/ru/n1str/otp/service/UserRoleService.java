package ru.n1str.otp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.n1str.otp.models.User;
import ru.n1str.otp.models.role.RoleUser;
import ru.n1str.otp.repository.UserRepository;

import java.util.HashSet;
import java.util.Set;

//Сервис для определения и назначения ролей пользователям

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRoleService {

    private final RoleService roleService;
    private final UserRepository userRepository;

    //Определяет и устанавливает роли для нового пользователя

    public boolean assignRolesForNewUser(User user) {
        String username = user.getUsername();
        Set<RoleUser> roles = new HashSet<>();
        
        // Проверяем, является ли логин "admin" и нет ли других администраторов
        if ("admin".equalsIgnoreCase(username) && !hasAdminUsers()) {
            RoleUser adminRole = roleService.findRole("ADMIN");
            roles.add(adminRole);
            log.info("Создание ПЕРВОГО администратора: {}", username);
            user.setRoleUsers(roles);
            return true;
        } else {
            // Для всех остальных пользователей или если админ уже есть
            RoleUser userRole = roleService.findRole("USER");
            roles.add(userRole);
            log.info("Создание обычного пользователя: {}", username);
            user.setRoleUsers(roles);
            return false;
        }
    }

    //Проверяет, есть ли уже пользователи с правами администратора

    public boolean hasAdminUsers() {
        return userRepository.findAll().stream()
                .anyMatch(user -> user.getRoleUsers().stream()
                        .anyMatch(role -> 
                            role.getNameRole().equals("ROLE_ADMIN") || 
                            role.getNameRole().equals("ADMIN")));
    }

}