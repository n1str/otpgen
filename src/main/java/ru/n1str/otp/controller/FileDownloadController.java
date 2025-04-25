package ru.n1str.otp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.n1str.otp.repository.UserRepository;
import ru.n1str.otp.service.OtpDownloadService;

@RestController
@RequestMapping("/api/otp/export")
@RequiredArgsConstructor
@Slf4j
public class FileDownloadController {

    private final OtpDownloadService otpDownloadService;
    private final UserRepository userRepository;


    //Экспорт истории OTP в формате CSV для текущего пользователя

    @GetMapping("/csv")
    public ResponseEntity<Resource> exportOtpCsv(Authentication authentication) {
        String username = authentication.getName();
        log.info("Запрос на экспорт OTP истории в CSV для пользователя: {}", username);
        
        Resource fileResource = otpDownloadService.exportOtpHistoryToCsv(username);
        
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"otp_history_" + username + ".csv\"")
                .body(fileResource);
    }

    //Экспорт истории OTP в формате CSV для всех пользователей (только для администраторов)

    @GetMapping("/admin/csv")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Resource> exportAllOtpCsv() {
        log.info("Запрос администратором на экспорт OTP истории всех пользователей в CSV");
        
        Resource fileResource = otpDownloadService.exportAllOtpHistoryToCsv();
        
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"all_otp_history.csv\"")
                .body(fileResource);
    }


    //Экспорт истории OTP в формате CSV для конкретного пользователя (только для администраторов)

    @GetMapping("/admin/csv/{username}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Resource> exportUserOtpCsvAsAdmin(@PathVariable String username) {
        log.info("Запрос администратором на экспорт OTP истории пользователя {} в CSV", username);
        
        if (!userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Пользователь не найден");
        }
        
        Resource fileResource = otpDownloadService.exportOtpHistoryToCsv(username);
        
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"otp_history_" + username + ".csv\"")
                .body(fileResource);
    }
}