package ru.n1str.otp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.n1str.otp.models.User;
import ru.n1str.otp.repository.OtpConfigurationRepository;
import ru.n1str.otp.repository.UserRepository;
import ru.n1str.otp.service.EmailService;
import ru.n1str.otp.service.OtpService;
import ru.n1str.otp.service.SmsService;

import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/otp")
@RequiredArgsConstructor
@Slf4j
public class OtpController {

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final OtpConfigurationRepository configRepo;

    //Верификация OTP кода для текущего аутентифицированного пользователя

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        
        String code = request.get("code");
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Map<String, Object> response = new HashMap<>();
        
        if (code == null || code.isEmpty()) {
            response.put("success", false);
            response.put("message", "Код должен быть указан");
            return ResponseEntity.badRequest().body(response);
        }
        
        boolean verified = otpService.verify(user, code);
        
        if (verified) {
            response.put("success", true);
            response.put("message", "Код подтвержден");
        } else {
            response.put("success", false);
            response.put("message", "Неверный код или истек срок действия");
        }
        
        return ResponseEntity.ok(response);
    }

    //Верификация OTP кода OTP кода для указанного пользователя

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateOtp(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String code = request.get("code");
        Map<String, Object> response = new HashMap<>();
        
        if (username == null || username.isEmpty() || code == null || code.isEmpty()) {
            response.put("success", false);
            response.put("message", "Имя пользователя и код должны быть указаны");
            return ResponseEntity.badRequest().body(response);
        }
        
        User user = userRepository.findByUsername(username)
                .orElse(null);
        
        if (user == null) {
            response.put("success", false);
            response.put("message", "Пользователь не найден");
            return ResponseEntity.badRequest().body(response);
        }
        
        boolean verified = otpService.verify(user, code);
        
        if (verified) {
            response.put("success", true);
            response.put("message", "Код подтвержден");
        } else {
            response.put("success", false);
            response.put("message", "Неверный код или истек срок действия");
        }
        
        return ResponseEntity.ok(response);
    }

    //Отправка OTP кода на email

    @PostMapping("/send-email")
    public ResponseEntity<Map<String, Object>> sendOtpEmail(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        
        String email = request.get("email");
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Map<String, Object> response = new HashMap<>();
        
        if (email == null || email.isEmpty()) {
            response.put("success", false);
            response.put("message", "Email должен быть указан");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            ru.n1str.otp.models.OtpCode otpCode = otpService.generateEmailOtpWithoutSending(user, email);

            emailService.sendOtpEmail(email, otpCode.getCode());
            
            response.put("success", true);
            response.put("message", "Код отправлен на email");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при отправке OTP на email: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Не удалось отправить код: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //Отправка OTP кода через SMS

    @PostMapping("/send-sms")
    public ResponseEntity<Map<String, Object>> sendOtpSms(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        
        String phone = request.get("phone");
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Map<String, Object> response = new HashMap<>();
        
        if (phone == null || phone.isEmpty()) {
            response.put("success", false);
            response.put("message", "Номер телефона должен быть указан");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            ru.n1str.otp.models.OtpCode otpCode = otpService.generateSmsOtpWithoutSending(user, phone);

            smsService.sendOtpCode(phone, otpCode.getCode());
            
            response.put("success", true);
            response.put("message", "Код отправлен по SMS");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при отправке OTP по SMS: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Не удалось отправить код: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 