package ru.n1str.otp.models.role;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import ru.n1str.otp.models.User;

import java.util.List;

@Entity
@Getter
@Setter
public class RoleUser {
    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true)
    private String nameRole;

    @ManyToMany(mappedBy = "roleUsers")
    List<User> userList;

    public RoleUser(String nameRole) {
        this.nameRole = nameRole;
    }

    public RoleUser() {

    }

    public String getName() {
        return nameRole;
    }
}
