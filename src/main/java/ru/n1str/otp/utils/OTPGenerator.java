package ru.n1str.otp.utils;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OTPGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generateOTP(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    public String generateOTP() {
        return generateOTP(6);
    }
}