package ru.n1str.otp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.n1str.otp.models.OtpConfiguration;

public interface OtpConfigurationRepository extends JpaRepository<OtpConfiguration, Long> {

}