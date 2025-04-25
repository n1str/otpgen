package ru.n1str.otp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.n1str.otp.utils.OTPGenerator;
import ru.n1str.otp.models.User;
import ru.n1str.otp.models.OtpCode;
import ru.n1str.otp.repository.UserRepository;
import org.springframework.context.annotation.Lazy;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {

    private final TelegramLinkService telegramLinkService;
    private final UserRepository userRepository;
    private final @Lazy OtpService otpService;
    private final OTPGenerator otpGenerator;

    private static final int OTP_EXPIRATION_MINUTES = 5;
    private final Map<Long, String> activeOtpCodes = new ConcurrentHashMap<>();
    private final Map<String, Long> linkTokens = new ConcurrentHashMap<>();

    @Value("${telegram.bot.username}")
    private String botUsername;

    public TelegramBotService(TelegramLinkService telegramLinkService,
                             UserRepository userRepository,
                             @Lazy OtpService otpService,
                             OTPGenerator otpGenerator,
                             @Value("${telegram.bot.token}") String botToken) {
        super(botToken);
        this.telegramLinkService = telegramLinkService;
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.otpGenerator = otpGenerator;
    }

    @PostConstruct
    public void initCommands() {
        try {
            List<BotCommand> commandList = new ArrayList<>();
            commandList.add(new BotCommand("/start", "Запустить бота"));
            commandList.add(new BotCommand("/code", "Получить OTP-код"));
            commandList.add(new BotCommand("/link", "Привязать аккаунт"));
            commandList.add(new BotCommand("/help", "Получить помощь"));

            execute(new SetMyCommands(commandList, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Ошибка при инициализации команд бота", e);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            log.info("Получено сообщение: {} от chatId: {}", messageText, chatId);

            if (messageText.startsWith("/start")) {
                String[] parts = messageText.split(" ", 2);

                if (parts.length > 1 && parts[1].startsWith("link_")) {
                    String token = parts[1].substring(5);
                    linkAccount(chatId, token);
                } else {
                    sendWelcomeMessage(chatId);
                }
            } else if (messageText.equals("/code") || messageText.equals("Получить код")) {
                sendOtpCode(chatId);
            } else if (messageText.equals("/help") || messageText.equals("Помощь")) {
                sendHelpMessage(chatId);
            } else if (messageText.startsWith("/link_")) {
                String token = messageText.substring(6);
                linkAccount(chatId, token);
            } else if (messageText.equals("/link") || messageText.equals("Привязать аккаунт")) {
                String token = UUID.randomUUID().toString();

                log.info("Генерация нового токена привязки для chatId: {}: {}", chatId, token);

                for (Map.Entry<String, Long> entry : linkTokens.entrySet()) {
                    if (entry.getValue().equals(chatId)) {
                        log.info("Удаляем устаревший токен для chatId {}: {}", chatId, entry.getKey());
                        linkTokens.remove(entry.getKey());
                    }
                }
                
                sendLinkCode(chatId, token);
            }
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = createMessageWithKeyboard(chatId,
                "👋 Добро пожаловать в сервис OTP-авторизации!\n\n" +
                        "Этот бот поможет вам получать коды подтверждения для авторизации в системе.\n\n" +
                        "Что вы можете сделать:\n" +
                        "🔹 Нажмите кнопку «Получить код» для генерации нового OTP-кода\n" +
                        "🔹 Если вы уже привязали свой аккаунт, коды будут автоматически приходить при запросе с сайта\n" +
                        "🔹 Для получения дополнительной информации нажмите «Помощь»");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке приветственного сообщения", e);
        }
    }

    private void sendHelpMessage(long chatId) {
        SendMessage message = createMessageWithKeyboard(chatId,
                "ℹ️ Справка по использованию бота:\n\n" +
                        "1️⃣ Для получения кода нажмите кнопку «Получить код» или используйте команду /code\n\n" +
                        "2️⃣ Если ваш аккаунт уже привязан к Telegram, система сможет автоматически отправлять вам коды\n\n" +
                        "3️⃣ Коды действительны в течение 5 минут после генерации\n\n" +
                        "4️⃣ При возникновении проблем обратитесь в техническую поддержку");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке справочного сообщения", e);
        }
    }

    private void linkAccount(long chatId, String token) {
        boolean success = telegramLinkService.linkTelegramAccount(token, chatId);

        if (success) {
            SendMessage message = createMessageWithKeyboard(chatId,
                    "✅ Ваш Telegram успешно привязан к аккаунту!\n\n" +
                            "Теперь вы можете получать коды подтверждения через этот чат. " +
                            "Система будет автоматически отправлять вам коды при авторизации.");

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке сообщения об успешной привязке", e);
            }
        } else {
            String newToken = UUID.randomUUID().toString();
            sendLinkCode(chatId, newToken);
            
            SendMessage message = createMessageWithKeyboard(chatId,
                    "ℹ️ Чтобы привязать Telegram к вашему аккаунту, скопируйте код выше и введите его на сайте.");

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке сообщения о неудачной привязке", e);
            }
        }
    }

    public void sendOtpCode(long chatId) {
        String otpCode = otpGenerator.generateOTP();
        activeOtpCodes.put(chatId, otpCode);

        SendMessage message = createMessageWithKeyboard(chatId,
                "🔐 Ваш код подтверждения: *" + otpCode + "*\n\n" +
                        "Код действителен в течение 5 минут. " +
                        "Введите его на странице авторизации.");

        message.enableMarkdown(true);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке OTP-кода", e);
        }
    }

    private SendMessage createMessageWithKeyboard(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Получить код"));
        row1.add(new KeyboardButton("Привязать аккаунт"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Помощь"));

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        return message;
    }

    // Метод для внешнего вызова, чтобы отправить код по chatId
    public boolean sendOtpForUser(long chatId) {
        try {
            User user = userRepository.findByTelegramChatId(chatId).orElse(null);
            if (user == null) {
                log.error("Пользователь с chatId {} не найден", chatId);
                return false;
            }

            OtpCode otpCode = otpService.generateTelegramOtpWithoutSending(user, String.valueOf(chatId));

            String code = otpCode.getCode();

            activeOtpCodes.put(chatId, code);

            SendMessage message = createMessageWithKeyboard(chatId,
                    "🔐 Ваш код подтверждения: *" + code + "*\n\n" +
                            "Код действителен в течение 5 минут. " +
                            "Введите его на странице авторизации.");
            
            message.enableMarkdown(true);
            
            try {
                execute(message);
                log.info("OTP код {} успешно отправлен пользователю через Telegram", code);
                return true;
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке сообщения с кодом: {}", e.getMessage(), e);
                return false;
            }
        } catch (Exception e) {
            log.error("Ошибка при отправке OTP-кода пользователю: {}", e.getMessage(), e);
            return false;
        }
    }

    // Метод для приема chatId как String и кода
    public void sendOtpCode(String chatIdStr, String code) {
        try {
            long chatId = Long.parseLong(chatIdStr);
            SendMessage message = createMessageWithKeyboard(chatId,
                    "🔐 Ваш код подтверждения: *" + code + "*\n\n" +
                            "Код действителен в течение 5 минут. " +
                            "Введите его на странице авторизации.");

            message.enableMarkdown(true);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке сообщения", e);
            }
        } catch (NumberFormatException e) {
            log.error("Невозможно преобразовать chatId в числовой формат: {}", chatIdStr, e);
        }
    }

    // Метод для проверки кода
    public boolean verifyOtp(long chatId, String code) {
        String storedCode = activeOtpCodes.get(chatId);
        if (storedCode != null && storedCode.equals(code)) {
            activeOtpCodes.remove(chatId);
            return true;
        }
        return false;
    }

    // Метод для отправки специального кода привязки с chatId и токеном
    public void sendLinkCode(long chatId, String token) {
        linkTokens.put(token, chatId);

        String linkCode = token;
        
        SendMessage message = createMessageWithKeyboard(chatId,
                "🔑 Ваш код для привязки аккаунта: *" + linkCode + "*\n\n" +
                        "Скопируйте этот код и вставьте его на сайте в поле 'Код из Telegram'.\n" +
                        "⚠️ Код действителен только для одной привязки.");

        message.enableMarkdown(true);

        try {
            execute(message);
            log.info("Отправлен код привязки для chatId: {} с токеном: {}", chatId, token);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке кода привязки", e);
        }
    }
    
    // Метод для получения chatId по токену
    public Long getChatIdByToken(String token) {
        log.info("Поиск chatId по токену: {}", token);
        Long chatId = linkTokens.get(token);
        
        if (chatId != null) {
            log.info("Найден chatId: {} для токена: {}", chatId, token);
        } else {
            log.warn("ChatId не найден для токена: {}. Текущие токены: {}", token, linkTokens.keySet());
        }
        
        return chatId;
    }
    
    // Временный метод для отладки - вывод всех активных токенов
    public Map<String, Long> getActiveLinkTokens() {
        log.info("Активные токены: {}", linkTokens);
        return linkTokens;
    }

    // Метод для удаления токена из хранилища
    public void removeToken(String token) {
        if (linkTokens.containsKey(token)) {
            Long chatId = linkTokens.remove(token);
            log.info("Токен {} удален из хранилища после успешной привязки для chatId {}", token, chatId);
        } else {
            log.warn("Попытка удалить несуществующий токен: {}", token);
        }
    }

    public void sendOtpForUser(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getTelegramChatId() == null) {
            log.warn("Невозможно отправить OTP код: пользователь {} не существует или не привязан к Telegram", username);
            return;
        }

        long chatId = user.getTelegramChatId();
        String otpCode = otpGenerator.generateOTP();

        activeOtpCodes.put(chatId, otpCode);

        String text = String.format("Ваш код подтверждения: *%s*\n\nКод действителен в течение %d минут.", 
                                   otpCode, OTP_EXPIRATION_MINUTES);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.enableMarkdown(true);

        try {
            execute(message);
            log.info("OTP код успешно отправлен пользователю {} через Telegram", username);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке OTP кода в Telegram: {}", e.getMessage());
        }
    }
}