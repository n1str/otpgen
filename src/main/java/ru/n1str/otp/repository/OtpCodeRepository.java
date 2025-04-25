package ru.n1str.otp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.n1str.otp.models.OtpCode;
import ru.n1str.otp.models.OtpStatus;
import ru.n1str.otp.models.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    List<OtpCode> findByUserAndStatus(User user, OtpStatus status);
    Optional<OtpCode> findByCodeAndStatus(String code, OtpStatus status);
    List<OtpCode> findByStatusAndExpiresAtBefore(OtpStatus status, LocalDateTime time);
    Optional<OtpCode> findByUserAndCodeAndStatus(User user, String code, OtpStatus status);
    List<OtpCode> findByUser(User user);
}