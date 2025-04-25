package ru.n1str.otp.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

//Реализация сервиса для работы с JWT токенами.

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${jwt.secret:defaultSecretKeyThatIsLongEnoughForHS512SignatureAlgorithm}")
    private String secretString;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String extractUsername(String token) {
        try {
            String username = extractClaim(token, Claims::getSubject);
            log.debug("Извлечено имя пользователя из токена: {}", username);
            return username;
        } catch (Exception e) {
            log.error("Ошибка при извлечении имени пользователя из токена: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public Date extractExpiration(String token) {
        try {
            Date expiration = extractClaim(token, Claims::getExpiration);
            log.debug("Извлечена дата истечения токена: {}", expiration);
            return expiration;
        } catch (Exception e) {
            log.error("Ошибка при извлечении даты истечения из токена: {}", e.getMessage());
            throw e;
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        try {
            final Claims claims = extractAllClaims(token);
            List<String> roles = (List<String>) claims.get("roles");
            log.debug("Извлечены роли из токена: {}", roles);
            return roles;
        } catch (Exception e) {
            log.error("Ошибка при извлечении ролей из токена: {}", e.getMessage());
            throw e;
        }
    }

    private Claims extractAllClaims(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
            log.debug("Успешно извлечены claims из токена. Subject: {}, Expiration: {}", 
                    claims.getSubject(), claims.getExpiration());
            return claims;
        } catch (Exception e) {
            log.error("Ошибка при парсинге JWT токена: {}", e.getMessage());
            throw e;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);
        boolean isExpired = expiration.before(new Date());
        log.debug("Проверка истечения токена. Дата истечения: {}, Текущая дата: {}, Истек: {}", 
                expiration, new Date(), isExpired);
        return isExpired;
    }

    @Override
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        Map<String, Object> claims = new HashMap<>();

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        
        claims.put("roles", roles);
        
        log.info("Генерация токена из Authentication для пользователя: {} с ролями: {}", 
                userDetails.getUsername(), roles);
                
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);
        
        log.debug("Создание нового токена для пользователя: {}. Дата выдачи: {}, Дата истечения: {}", 
                subject, now, expiryDate);
        
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
                
        log.debug("Сгенерирован токен (первые 20 символов): {}...", 
                token.length() > 20 ? token.substring(0, 20) : token);
                
        return token;
    }

    @Override
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            boolean isUsernameValid = username.equals(userDetails.getUsername());
            boolean isNotExpired = !isTokenExpired(token);
            boolean isValid = isUsernameValid && isNotExpired;
            
            log.debug("Валидация токена для пользователя {}. Имя пользователя валидно: {}, Не истек: {}, Итог: {}", 
                    username, isUsernameValid, isNotExpired, isValid);
                    
            return isValid;
        } catch (Exception e) {
            log.error("Валидация токена не удалась: {}", e.getMessage());
            return false;
        }
    }
} 