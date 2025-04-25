package ru.n1str.otp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.n1str.otp.models.User;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    //Ищет пользователя по логину (username).

    Optional<User> findByUsername (String login);

    Optional<User> findByTelegramLinkToken(String token);

    //Ищет пользователя по идентификатору Telegram чата.

    Optional<User> findByTelegramChatId(Long chatId);
}
