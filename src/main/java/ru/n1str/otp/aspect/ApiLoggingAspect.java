package ru.n1str.otp.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@Slf4j
public class ApiLoggingAspect {

    @Around("@within(org.springframework.web.bind.annotation.RestController) || " +
            "@within(org.springframework.stereotype.Controller)")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        String username = "anonymous";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            username = auth.getName();
        }

        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!headerName.equalsIgnoreCase("authorization") &&
                    !headerName.equalsIgnoreCase("cookie")) {
                headers.put(headerName, request.getHeader(headerName));
            }
        }

        log.info("API Request: {} {} | User: {} | IP: {} | Headers: {} | Args: {}",
                request.getMethod(),
                request.getRequestURI(),
                username,
                request.getRemoteAddr(),
                headers,
                Arrays.toString(joinPoint.getArgs()));

        long startTime = System.currentTimeMillis();
        Object result;

        try {
            result = joinPoint.proceed();
            log.info("API Response: {} {} | User: {} | Status: OK | Time: {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    username,
                    System.currentTimeMillis() - startTime);

            return result;
        } catch (Exception e) {
            log.error("API Exception: {} {} | User: {} | Status: ERROR | Time: {}ms | Error: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    username,
                    System.currentTimeMillis() - startTime,
                    e.getMessage(),
                    e);

            throw e;
        }
    }
}