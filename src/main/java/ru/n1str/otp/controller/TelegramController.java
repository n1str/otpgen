package ru.n1str.otp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.n1str.otp.service.TelegramBotService;
import ru.n1str.otp.service.TelegramLinkService;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.util.Map;
import ru.n1str.otp.repository.UserRepository;
import ru.n1str.otp.models.User;

import java.util.HashMap;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramController {

    private final TelegramBotService telegramBotService;
    private final TelegramLinkService telegramLinkService;
    private final UserRepository userRepository;



    //Генерация токена для связывания аккаунта с Telegram (требует JWT)

    @GetMapping("/generate-link-token")
    public ResponseEntity<Map<String, String>> generateLinkToken(Authentication authentication) {
        String username = authentication.getName();
        String token = telegramLinkService.generateLinkToken(username);

        String botUsername = telegramBotService.getBotUsername();
        String telegramDeepLink = "https://t.me/" + botUsername + "?start=link_" + token;

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("deepLink", telegramDeepLink);

        return ResponseEntity.ok(response);
    }


    //Проверка статуса связи с Telegram (требует JWT)

    @GetMapping("/status")
    public ResponseEntity<?> getTelegramStatus(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        
        Long chatId = user.getTelegramChatId();
        boolean isLinked = chatId != null && chatId > 0;
        
        return ResponseEntity.ok(Map.of("linked", isLinked));
    }


    //Отправка OTP через Telegram (требует JWT)

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(Authentication authentication) {
        String username = authentication.getName();
        Long chatId = telegramLinkService.getUserTelegramChatId(username);

        if (chatId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Telegram не привязан к вашему аккаунту");
            return ResponseEntity.badRequest().body(response);
        }

        boolean success = telegramBotService.sendOtpForUser(chatId);

        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("success", true);
            response.put("message", "Код отправлен в ваш Telegram");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Не удалось отправить OTP код");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //Прроверка кода и связывание аккаунта Telegram

    @PostMapping("/verify-link")
    public ResponseEntity<Map<String, Object>> verifyLink(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        
        String code = request.get("code");
        String username = authentication.getName();
        
        Map<String, Object> response = new HashMap<>();
        
        if (code == null || code.isEmpty()) {
            response.put("success", false);
            response.put("message", "Код должен быть указан");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            log.info("Начало проверки кода Telegram: '{}' для пользователя: '{}'", code, username);

            Map<String, Long> activeTokens = telegramBotService.getActiveLinkTokens();
            log.info("Активные токены привязки: {}", activeTokens);

            Long chatId = telegramBotService.getChatIdByToken(code);
            
            if (chatId == null) {
                log.warn("ChatId не найден для кода: '{}' (пользователь: '{}')", code, username);
                response.put("success", false);
                response.put("message", "Неверный код или истек срок его действия. Пожалуйста, получите новый код, нажав на кнопку \"Привязать аккаунт\" в Telegram боте.");
                return ResponseEntity.ok(response);
            }
            
            log.info("Найден chatId: {} для кода: '{}' (пользователь: '{}')", chatId, code, username);

            telegramLinkService.directLinkTelegramAccount(username, chatId);
            log.info("Привязка напрямую выполнена для пользователя {} с chatId {}", username, chatId);

            telegramBotService.removeToken(code);
            
            response.put("success", true);
            response.put("message", "Telegram аккаунт успешно привязан");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при связывании аккаунта: ", e);
            response.put("success", false);
            response.put("message", "Ошибка при связывании аккаунта: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}