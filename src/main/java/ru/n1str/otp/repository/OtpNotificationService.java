package ru.n1str.otp.repository;

public interface OtpNotificationService {
    void sendCode(String destination, String code);
}