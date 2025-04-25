package ru.n1str.otp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.n1str.otp.models.role.RoleUser;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleUser, Long> {

    //Ищет роль по названию (например, "USER" или "ADMIN").

    public Optional<RoleUser> findByNameRole(String nameRole);
}
