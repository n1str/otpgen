package ru.n1str.otp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ru.n1str.otp.models.OtpCode;
import ru.n1str.otp.models.User;
import ru.n1str.otp.repository.OtpCodeRepository;
import ru.n1str.otp.repository.UserRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;


//Сервис для экспорта истории OTP в формате CSV

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpDownloadService {
    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    

    //Экспорт истории OTP в CSV для пользователя

    public Resource exportOtpHistoryToCsv(String username) {
        log.info("Экспорт истории OTP в CSV для пользователя: {}", username);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        
        return new ByteArrayResource(generateOtpHistoryCsv(user).getBytes());
    }

    //Экспорт всей истории OTP в CSV (для администраторов)

    public Resource exportAllOtpHistoryToCsv() {
        log.info("Экспорт всей истории OTP в CSV");
        
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Пользователь,Код,Статус,Канал,Создан,Действителен до,ID операции\n");
        
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            List<OtpCode> codes = otpCodeRepository.findByUser(user);
            for (OtpCode code : codes) {
                csv.append(code.getId())
                   .append(",")
                   .append(user.getUsername())
                   .append(",")
                   .append(code.getCode())
                   .append(",")
                   .append(code.getStatus())
                   .append(",")
                   .append(code.getChannel())
                   .append(",")
                   .append(code.getCreatedAt().format(formatter))
                   .append(",")
                   .append(code.getExpiresAt().format(formatter))
                   .append(",")
                   .append(code.getOperationId())
                   .append("\n");
            }
        }
        
        return new ByteArrayResource(csv.toString().getBytes());
    }

    //Генерирует CSV с историей OTP кодов для пользователя

    private String generateOtpHistoryCsv(User user) {
        List<OtpCode> codes = otpCodeRepository.findByUser(user);
        
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Код,Статус,Канал,Создан,Действителен до,ID операции\n");
        
        if (codes.isEmpty()) {
            return csv.toString();
        }
        
        for (OtpCode code : codes) {
            csv.append(code.getId())
               .append(",")
               .append(code.getCode())
               .append(",")
               .append(code.getStatus())
               .append(",")
               .append(code.getChannel())
               .append(",")
               .append(code.getCreatedAt().format(formatter))
               .append(",")
               .append(code.getExpiresAt().format(formatter))
               .append(",")
               .append(code.getOperationId())
               .append("\n");
        }
        
        log.info("Сгенерирована CSV история OTP для пользователя {}, найдено {} записей", 
                user.getUsername(), codes.size());
        return csv.toString();
    }
}