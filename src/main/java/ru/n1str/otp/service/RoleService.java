package ru.n1str.otp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ru.n1str.otp.models.role.DefaultRoleNotFound;
import ru.n1str.otp.models.role.RoleUser;
import ru.n1str.otp.repository.RoleRepository;

import jakarta.annotation.PostConstruct;


//Отвечает за работу с ролями пользователей

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {
    @Autowired
    RoleRepository roleRepository;

    //Ищет роль по названию. Если не найдена — выбрасывает исключение.

    public RoleUser findRole(String nameRole){

        String roleName = nameRole.startsWith("ROLE_") ? nameRole : "ROLE_" + nameRole;
        
        try {
            return roleRepository.findByNameRole(roleName)
                    .orElseThrow(() -> new DefaultRoleNotFound("Роль " + roleName + " не найдена"));
        } catch (DefaultRoleNotFound ex) {
            return roleRepository.findByNameRole(nameRole)
                    .orElseThrow(() -> new DefaultRoleNotFound("Роль " + nameRole + " не найдена"));
        }
    }

    //Проверяет наличие роли, если нет — создаёт новую.

    public void checkRoleOrCreate(String arg){
        String roleName = arg.startsWith("ROLE_") ? arg : "ROLE_" + arg;
        
        try {
            RoleUser role = roleRepository.findByNameRole(roleName)
                    .orElseThrow(() -> new DefaultRoleNotFound("Не найдена"));
            
            log.info("Предустановленная роль: {} уже существует", role.getNameRole());
        } catch (DefaultRoleNotFound ex1) {
            try {
                RoleUser role = roleRepository.findByNameRole(arg)
                        .orElseThrow(() -> new DefaultRoleNotFound("Не найдена"));
                
                log.info("Предустановленная роль без префикса: {} существует, изменение не требуется", 
                        role.getNameRole());
            } catch (DefaultRoleNotFound ex2) {
                log.warn("Отсутствует предустановленная роль {}, создаём...", roleName);
                roleRepository.save(new RoleUser(roleName));
            }
        }
    }

    //Инициализация стандартных ролей приложения.

    @PostConstruct
    public void initDefaultRoles() {
        log.info("Инициализация ролей пользователей");
        checkRoleOrCreate("USER");
        checkRoleOrCreate("ADMIN");
    }
}
