package ru.n1str.otp.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_code")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @Enumerated(EnumType.STRING)
    private OtpStatus status;

    @Enumerated(EnumType.STRING)
    private OtpChannel channel;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    @ManyToOne
    private User user;

    private String operationId;

    public enum OtpChannel {
        SMS, EMAIL, TELEGRAM, FILE
    }
}