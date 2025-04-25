package ru.n1str.otp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.n1str.otp.models.OtpCode;
import ru.n1str.otp.models.OtpConfiguration;
import ru.n1str.otp.models.OtpStatus;
import ru.n1str.otp.models.User;
import ru.n1str.otp.repository.OtpCodeRepository;
import ru.n1str.otp.repository.OtpConfigurationRepository;
import ru.n1str.otp.utils.OTPGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {
    private final OtpCodeRepository otpCodeRepository;
    private final OtpConfigurationRepository configRepository;
    private final OTPGenerator otpGenerator;
    private final SmsService smsService;
    private final EmailService emailService;
    private final TelegramBotService telegramService;


    //Получает или создает конфигурацию по умолчанию

    public OtpConfiguration getOrCreateDefaultConfig() {
        return configRepository.findById(1L).orElseGet(() -> {
            OtpConfiguration config = new OtpConfiguration();
            config.setId(1L);
            config.setCodeLength(6);
            config.setLifetimeMinutes(5);
            return configRepository.save(config);
        });
    }


    //Генерирует и отправляет OTP через SMS

    @Transactional
    public OtpCode generateAndSendSms(User user, String phoneNumber) {
        return generateAndSendOtp(user, OtpCode.OtpChannel.SMS, phoneNumber);
    }

    //Генерирует и отправляет OTP через Email

    @Transactional
    public OtpCode generateAndSendEmail(User user, String email) {
        return generateAndSendOtp(user, OtpCode.OtpChannel.EMAIL, email);
    }

    //Генерирует и отправляет OTP через Telegram

    @Transactional
    public OtpCode generateAndSendTelegram(User user, String chatId) {
        return generateAndSendOtp(user, OtpCode.OtpChannel.TELEGRAM, chatId);
    }

    //Генерирует и сохраняет OTP в файл

    @Transactional
    public OtpCode generateAndSaveToFile(User user, String filename) {
        return generateAndSendOtp(user, OtpCode.OtpChannel.FILE, filename);
    }

    //Генерирует OTP и отправляет через выбранный канал

    @Transactional
    public OtpCode generateAndSendOtp(User user, OtpCode.OtpChannel channel, String destination) {
        List<OtpCode> activeCodes = otpCodeRepository.findByUserAndStatus(user, OtpStatus.ACTIVE);
        for (OtpCode old : activeCodes) {
            old.setStatus(OtpStatus.EXPIRED);
            otpCodeRepository.save(old);
        }

        OtpConfiguration config = getOrCreateDefaultConfig();

        String code = otpGenerator.generateOTP(config.getCodeLength());

        OtpCode otpCode = new OtpCode();
        otpCode.setCode(code);
        otpCode.setStatus(OtpStatus.ACTIVE);
        otpCode.setCreatedAt(LocalDateTime.now());
        otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(config.getLifetimeMinutes()));
        otpCode.setUser(user);
        otpCode.setOperationId(UUID.randomUUID().toString());
        otpCode.setChannel(channel);

        otpCode = otpCodeRepository.save(otpCode);

        switch (channel) {
            case SMS:
                smsService.sendOtpCode(destination, code);
                log.info("Generated and sent OTP {} via SMS to {}", code, destination);
                break;
            case EMAIL:
                emailService.sendOtpEmail(destination, code);
                log.info("Generated and sent OTP {} via Email to {}", code, destination);
                break;
            case TELEGRAM:
                telegramService.sendOtpCode(destination, code);
                log.info("Generated and sent OTP {} via Telegram to chat {}", code, destination);
                break;
        }

        return otpCode;
    }

    //Проверяет OTP код

    @Transactional
    public boolean verify(User user, String code) {
        log.info("Попытка верификации кода для пользователя {}: код={}", user.getUsername(), code);

        List<OtpCode> activeCodes = otpCodeRepository.findByUserAndStatus(user, OtpStatus.ACTIVE);
        log.info("Всего активных кодов для пользователя {}: {}", user.getUsername(), activeCodes.size());
        
        for (OtpCode activeCode : activeCodes) {
            log.info("Активный код: {}, создан: {}, истекает: {}", 
                    activeCode.getCode(), activeCode.getCreatedAt(), activeCode.getExpiresAt());
        }
        
        Optional<OtpCode> otpOpt = otpCodeRepository.findByUserAndCodeAndStatus(user, code, OtpStatus.ACTIVE);

        if (otpOpt.isEmpty()) {
            log.warn("No active OTP found for user {} with code {}", user.getUsername(), code);
            return false;
        }

        OtpCode otp = otpOpt.get();
        log.info("Найден код: {}, создан: {}, истекает: {}", 
                otp.getCode(), otp.getCreatedAt(), otp.getExpiresAt());

        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            otp.setStatus(OtpStatus.EXPIRED);
            otpCodeRepository.save(otp);
            log.warn("OTP expired for user {}. Expiry time: {}, Current time: {}", 
                    user.getUsername(), otp.getExpiresAt(), LocalDateTime.now());
            return false;
        }

        otp.setStatus(OtpStatus.USED);
        otpCodeRepository.save(otp);
        log.info("OTP verified successfully for user {}", user.getUsername());

        return true;
    }

    //Периодически помечает просроченные коды как EXPIRED

    @Scheduled(fixedRate = 60_000) // Каждую минуту
    public void expireOldCodes() {
        List<OtpCode> expired = otpCodeRepository.findByStatusAndExpiresAtBefore(
                OtpStatus.ACTIVE, LocalDateTime.now());

        for (OtpCode code : expired) {
            code.setStatus(OtpStatus.EXPIRED);
            otpCodeRepository.save(code);
        }

        if (!expired.isEmpty()) {
            log.info("Marked {} expired OTP codes", expired.size());
        }
    }

    //Генерирует OTP для Telegram

    @Transactional
    public OtpCode generateTelegramOtpWithoutSending(User user, String chatId) {
        List<OtpCode> activeCodes = otpCodeRepository.findByUserAndStatus(user, OtpStatus.ACTIVE);
        for (OtpCode old : activeCodes) {
            old.setStatus(OtpStatus.EXPIRED);
            otpCodeRepository.save(old);
        }

        OtpConfiguration config = getOrCreateDefaultConfig();

        String code = otpGenerator.generateOTP(config.getCodeLength());

        OtpCode otpCode = new OtpCode();
        otpCode.setCode(code);
        otpCode.setStatus(OtpStatus.ACTIVE);
        otpCode.setCreatedAt(LocalDateTime.now());
        otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(config.getLifetimeMinutes()));
        otpCode.setUser(user);
        otpCode.setOperationId(UUID.randomUUID().toString());
        otpCode.setChannel(OtpCode.OtpChannel.TELEGRAM);

        otpCode = otpCodeRepository.save(otpCode);
        log.info("Generated OTP {} for Telegram chat {}", code, chatId);

        return otpCode;
    }

    //Генерирует OTP для SMS

    @Transactional
    public OtpCode generateSmsOtpWithoutSending(User user, String phoneNumber) {
        List<OtpCode> activeCodes = otpCodeRepository.findByUserAndStatus(user, OtpStatus.ACTIVE);
        for (OtpCode old : activeCodes) {
            old.setStatus(OtpStatus.EXPIRED);
            otpCodeRepository.save(old);
        }

        OtpConfiguration config = getOrCreateDefaultConfig();

        String code = otpGenerator.generateOTP(config.getCodeLength());

        OtpCode otpCode = new OtpCode();
        otpCode.setCode(code);
        otpCode.setStatus(OtpStatus.ACTIVE);
        otpCode.setCreatedAt(LocalDateTime.now());
        otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(config.getLifetimeMinutes()));
        otpCode.setUser(user);
        otpCode.setOperationId(UUID.randomUUID().toString());
        otpCode.setChannel(OtpCode.OtpChannel.SMS);

        otpCode = otpCodeRepository.save(otpCode);
        log.info("Generated OTP {} for SMS to phone number {}", code, phoneNumber);

        return otpCode;
    }

    //Генерирует OTP для Email

    @Transactional
    public OtpCode generateEmailOtpWithoutSending(User user, String email) {
        List<OtpCode> activeCodes = otpCodeRepository.findByUserAndStatus(user, OtpStatus.ACTIVE);
        for (OtpCode old : activeCodes) {
            old.setStatus(OtpStatus.EXPIRED);
            otpCodeRepository.save(old);
        }

        OtpConfiguration config = getOrCreateDefaultConfig();

        String code = otpGenerator.generateOTP(config.getCodeLength());

        OtpCode otpCode = new OtpCode();
        otpCode.setCode(code);
        otpCode.setStatus(OtpStatus.ACTIVE);
        otpCode.setCreatedAt(LocalDateTime.now());
        otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(config.getLifetimeMinutes()));
        otpCode.setUser(user);
        otpCode.setOperationId(UUID.randomUUID().toString());
        otpCode.setChannel(OtpCode.OtpChannel.EMAIL);


        otpCode = otpCodeRepository.save(otpCode);
        log.info("Generated OTP {} for EMAIL to {}", code, email);

        return otpCode;
    }
}