package ru.n1str.otp.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Properties;

@Service
@Slf4j
public class EmailService {
    private final String username;
    private final String password;
    private final String fromEmail;
    private final Session session;

    public EmailService() {
        Properties config = loadConfig();
        this.username = config.getProperty("email.username");
        this.password = config.getProperty("email.password");
        this.fromEmail = config.getProperty("email.from");

        Properties sessionProps = new Properties();
        sessionProps.put("mail.smtp.auth", config.getProperty("mail.smtp.auth"));
        sessionProps.put("mail.smtp.starttls.enable", config.getProperty("mail.smtp.starttls.enable"));
        sessionProps.put("mail.smtp.host", config.getProperty("mail.smtp.host"));
        sessionProps.put("mail.smtp.port", config.getProperty("mail.smtp.port"));
        sessionProps.put("mail.smtp.socketFactory.class", config.getProperty("mail.smtp.socketFactory.class"));
        sessionProps.put("mail.smtp.socketFactory.port", config.getProperty("mail.smtp.socketFactory.port"));

        this.session = Session.getInstance(sessionProps, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try {
            props.load(getClass().getClassLoader().getResourceAsStream("email.properties"));
            return props;
        } catch (IOException e) {
            log.error("Failed to load email configuration", e);
            throw new RuntimeException("Failed to load email configuration", e);
        }
    }

    public void sendOtpCode(String toEmail, String code) {
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject("Ваш код подтверждения");

            String htmlContent =
                    "<html>" +
                            "<head>" +
                            "  <style>" +
                            "    body { font-family: Arial, sans-serif; }" +
                            "    .container { padding: 20px; max-width: 600px; margin: 0 auto; }" +
                            "    .header { text-align: center; padding: 10px; }" +
                            "    .code-box { text-align: center; padding: 20px; background-color: #f7f7f7; " +
                            "              border-radius: 5px; margin: 20px 0; }" +
                            "    .code { font-size: 30px; font-weight: bold; letter-spacing: 5px; color: #0d6efd; }" +
                            "    .footer { font-size: 12px; color: #666; text-align: center; margin-top: 20px; }" +
                            "  </style>" +
                            "</head>" +
                            "<body>" +
                            "  <div class='container'>" +
                            "    <div class='header'>" +
                            "      <h2>Код подтверждения</h2>" +
                            "    </div>" +
                            "    <p>Здравствуйте!</p>" +
                            "    <p>Для завершения процесса авторизации используйте следующий код:</p>" +
                            "    <div class='code-box'>" +
                            "      <div class='code'>" + code + "</div>" +
                            "    </div>" +
                            "    <p>Код действителен в течение 5 минут.</p>" +
                            "    <p>Если вы не запрашивали этот код, просто проигнорируйте это сообщение.</p>" +
                            "    <div class='footer'>" +
                            "      Это автоматическое сообщение, пожалуйста, не отвечайте на него." +
                            "    </div>" +
                            "  </div>" +
                            "</body>" +
                            "</html>";

            message.setContent(htmlContent, "text/html; charset=UTF-8");

            Transport.send(message);
            log.info("OTP code sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendOtpEmail(String toEmail, String code) {
        sendOtpCode(toEmail, code);
    }
}