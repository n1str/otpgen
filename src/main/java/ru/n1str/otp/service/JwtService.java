package ru.n1str.otp.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.List;


//Сервис для работы с JWT токенами.

public interface JwtService {

    String extractUsername(String token);

    Date extractExpiration(String token);

    List<String> extractRoles(String token);

    String generateToken(Authentication authentication);

    boolean isTokenValid(String token, UserDetails userDetails);
} 