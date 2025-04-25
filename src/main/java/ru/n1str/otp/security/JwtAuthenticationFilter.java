package ru.n1str.otp.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.n1str.otp.service.JwtService;
import ru.n1str.otp.service.AuthUserService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


//Фильтр для JWT аутентификации

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthUserService authUserService;
    private final JwtAuthenticationProvider jwtAuthProvider;
    
    // Публичные эндпоинты - только для логина и регистрации
    private final List<String> publicEndpoints = Arrays.asList(
            "/api/jwt/auth",
            "/api/auth/register"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                   @NonNull HttpServletResponse response,
                                   @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            log.debug("Обработка запроса в JWT фильтре: {}", path);

            if (isPublicEndpoint(path) || request.getMethod().equals("OPTIONS")) {
                filterChain.doFilter(request, response);
                return;
            }

            String jwt = extractJwtFromRequest(request);
            
            if (jwt != null) {
                try {
                    String username = jwtService.extractUsername(jwt);
                    log.debug("Извлечено имя пользователя из JWT: {}", username);
                    
                    Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();

                    if (existingAuth == null || !existingAuth.isAuthenticated() || 
                            existingAuth.getPrincipal().equals("anonymousUser")) {

                        UserDetails userDetails = authUserService.loadUserByUsername(username);

                        if (jwtService.isTokenValid(jwt, userDetails)) {
                            JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                                    userDetails,
                                    jwt,
                                    userDetails.getAuthorities()
                            );

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            log.debug("JWT аутентификация успешна для пользователя: {}", username);
                        } else {
                            log.warn("Невалидный JWT токен для пользователя: {}", username);
                            handleJwtError(response, "Невалидный JWT токен");
                            return;
                        }
                    } else {
                        log.debug("Пользователь уже аутентифицирован: {}, пропускаем JWT аутентификацию", 
                                existingAuth.getName());
                    }
                } catch (ExpiredJwtException e) {
                    log.warn("JWT токен истек: {}", e.getMessage());
                    handleJwtError(response, "JWT токен истек");
                    return;
                } catch (MalformedJwtException | SignatureException e) {
                    log.warn("Невалидный JWT формат/подпись: {}", e.getMessage());
                    handleJwtError(response, "Невалидный JWT формат или подпись");
                    return;
                } catch (UsernameNotFoundException e) {
                    log.warn("Пользователь из JWT не найден: {}", e.getMessage());
                    handleJwtError(response, "Пользователь из JWT не найден");
                    return;
                } catch (Exception e) {
                    log.error("Не удалось установить аутентификацию по JWT: {}", e.getMessage(), e);
                    handleJwtError(response, "Ошибка аутентификации JWT");
                    return;
                }
            } else {
                log.debug("JWT токен не найден в запросе: {}", path);
                handleJwtError(response, "JWT токен не предоставлен");
                return;
            }
        } catch (Exception e) {
            log.error("Непредвиденная ошибка в JWT фильтре: {}", e.getMessage(), e);
            handleJwtError(response, "Внутренняя ошибка сервера аутентификации");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        if (isPublicEndpoint(path)) {
            log.debug("Публичный эндпоинт: {} - пропускаем JWT фильтр", path);
            return true;
        }

        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }

        return false;
    }

    //Проверяет, является ли эндпоинт публичным

    private boolean isPublicEndpoint(String path) {
        return publicEndpoints.contains(path);
    }

    //Извлекает JWT токен из запроса

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    //Обрабатывает ошибки JWT аутентификации

    private void handleJwtError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}