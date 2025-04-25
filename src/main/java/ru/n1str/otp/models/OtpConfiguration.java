package ru.n1str.otp.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpConfiguration {
    @Id
    private Long id;

    private Integer codeLength;

    private Integer lifetimeMinutes;
}