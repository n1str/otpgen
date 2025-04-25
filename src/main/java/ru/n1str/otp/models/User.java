package ru.n1str.otp.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.n1str.otp.models.role.RoleUser;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "user_table")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_table_seq")
    @SequenceGenerator(name = "user_table_seq", sequenceName = "user_table_seq", allocationSize = 1)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "m2m_role_table",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "roles_id")
    )
    private Set<RoleUser> roleUsers;

    private boolean enabled;

    private boolean accountNonExpired;

    private boolean accountNonLocked;

    private boolean credentialsNonExpired;

    private Long telegramChatId;

    @Column(unique = true)
    private String telegramLinkToken;

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return getRoleUsers().stream()
                .map(role -> {
                    String roleName = role.getName();
                    String authorityName = roleName.startsWith("ROLE_") 
                        ? roleName 
                        : "ROLE_" + roleName;
                    return new SimpleGrantedAuthority(authorityName);
                })
                .collect(Collectors.toList());
    }
}