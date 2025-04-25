# Сервис OTP Аутентификации

Cервис аутентификации с одноразовыми паролями (OTP), созданный на Spring Boot, обеспечивающий безопасную многофакторную аутентификацию через различные каналы, включая Email, SMS и Telegram.

## Содержание

- [О проекте](#о-проекте)
  - [Особенности распределения административных прав](#особенности-распределения-административных-прав)
  - [Механизмы доставки OTP](#механизмы-доставки-otp)
- [Структура проекта](#структура-проекта)
- [Возможности](#возможности)
- [Предварительные требования](#предварительные-требования)
- [Руководство по установке и тестированию](#руководство-по-установке-и-тестированию)
- [API документация](#api-документация)
- [Устранение проблем](#устранение-типичных-проблем)
- [Дополнительные рекомендации](#дополнительные-рекомендации)

## О проекте

Данный проект представляет собой полнофункциональный сервис аутентификации, который использует механизм одноразовых паролей (OTP) для обеспечения безопасности учетных записей пользователей. Система поддерживает несколько каналов доставки OTP: электронная почта, SMS и Telegram.

Для работы SMS функциональности в тестовом или отладочном режиме проект использует эмулятор SMPP - [SMPPSim](https://github.com/delhee/SMPPSim/releases/tag/3.0.0).

### Особенности распределения административных прав

В системе реализован уникальный механизм назначения административных прав:
- Административные права может получить **только один пользователь** в системе
- Права администратора автоматически присваиваются **первому** пользователю, который регистрируется с именем `admin`
- Все последующие пользователи, даже если они используют имя `admin`, получают только обычные права пользователя
- Невозможно создать второго администратора в системе
- Админ может управлять пользователями, настраивать параметры OTP и получать доступ к истории OTP всех пользователей

### Механизмы доставки OTP

#### Email-интеграция

Сервис использует стандартный протокол SMTP для отправки электронных писем с OTP-кодами:

- Настройка через файл `email.properties`, где задаются параметры SMTP-сервера и учетные данные
- Поддерживает TLS/SSL для безопасной передачи данных
- Отправляет красиво оформленные HTML-письма, содержащие OTP-код и инструкции
- Встроенная обработка ошибок и логирование попыток отправки
- По умолчанию настроен для работы с Gmail SMTP

#### SMS-интеграция (SMPP)

Для отправки SMS используется протокол SMPP (Short Message Peer-to-Peer):

- Реализована через библиотеку OpenSMPP
- Настройка через файл `sms.properties`, где задаются параметры подключения к SMPP-серверу
- В режиме разработки использует эмулятор SMPPSim для тестирования без реального SMS-шлюза
- Поддерживает основные операции SMPP: привязка (bind), отправка сообщений и отвязка (unbind)
- Автоматическое закрытие соединений для освобождения ресурсов

#### Telegram-интеграция

Интеграция с Telegram реализована на базе Telegram Bot API и предлагает наиболее комплексный механизм взаимодействия:

- Основана на API Telegram Long Polling Bot
- Использует два взаимосвязанных сервиса: `TelegramBotService` и `TelegramLinkService`
- Поддерживает команды, клавиатуру и интерактивное взаимодействие
- Предлагает двусторонний канал связи для удобства пользователя

**Механизм связывания аккаунта с Telegram:**
1. **Инициация связывания:** пользователь инициирует связывание через API, ссылка с включеннным в нее токеном
2. **Процесс связывания:** сервис связывает идентификатор Telegram-чата с аккаунтом
3. **Хранение связи:** идентификатор чата сохраняется в базе данных
4. **Отправка OTP:** при запросе OTP код отправляется непосредственно в Telegram-чат пользователя

## Структура проекта

Проект следует принципам чистой архитектуры и организован в следующие пакеты:

### Основные компоненты

- **controller** - REST-контроллеры для обработки HTTP-запросов:
  - `AdminController` - управление пользователями и настройками OTP (только для администраторов)
  - `AuthRestController` - регистрация пользователей и управление профилем
  - `JwtAuthController` - JWT-аутентификация
  - `OtpController` - операции с OTP (отправка, верификация)
  - `TelegramController` - интеграция с Telegram-ботом
  - `FileDownloadController` - экспорт данных OTP в CSV

- **service** - бизнес-логика приложения:
  - `OtpService` - работа с OTP кодами (генерация, проверка)
  - `EmailService` - отправка OTP по электронной почте
  - `SmsService` - отправка OTP через SMS (SMPP)
  - `TelegramBotService` - отправка OTP через Telegram
  - `JwtService` - работа с JWT токенами
  - `UserRoleService` - управление ролями пользователей

- **models** - модели данных
- **repository** - интерфейсы для работы с базой данных
- **security** - конфигурация безопасности
- **configuration** - конфигурационные классы

## Возможности

- **Аутентификация пользователей**
  - Регистрация и JWT-аутентификация
  - Управление профилем пользователя
  - Функция смены пароля

- **Каналы доставки OTP**
  - Доставка OTP по электронной почте
  - Доставка OTP через SMS
  - Доставка OTP через Telegram

- **Административные функции**
  - Управление пользователями
  - Настройки конфигурации OTP
  - Экспорт истории OTP

## Предварительные требования

- JDK 21
- Maven
- **PostgreSQL** (обязательно) - необходимо иметь установленную локальную базу данных
- SMTP сервер для отправки электронной почты
- SMPP сервер для SMS (для тестирования используется [SMPPSim](https://github.com/delhee/SMPPSim/releases/tag/3.0.0))
- Токен Telegram Bot API (опционально)

## Руководство по установке и тестированию

### 1. Настройка базы данных

1. Убедитесь, что PostgreSQL установлен и запущен
2. Создайте базу данных и пользователя:

```sql
CREATE DATABASE otp_db;
CREATE USER postgres WITH PASSWORD 'ПАРОЛЬ';
GRANT ALL PRIVILEGES ON DATABASE otp_db TO postgres;
```

### 2. Настройка проекта

1. Клонируйте репозиторий:

```bash
git clone https://github.com/yourusername/otp-service.git
cd otp-service
```

2. Настройте конфигурационные файлы:

**application.properties:**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/otp_db
spring.datasource.username=postgres
spring.datasource.password=ПАРОЛЬ
spring.jpa.hibernate.ddl-auto=create-drop

jwt.expiration=86400000
jwt.secret=вашСекретныйКлюч

telegram.bot.username=ИмяВашегоБота
telegram.bot.token=ТокенВашегоБота
```

**email.properties:**
```properties
email.username=your-email@gmail.com
email.password=your-app-password(ЭТО ПАРОЛЬ НЕ ОТ ПОЧТЫ, А СПЕЦИЛЬНЫЙ ВЫПУЩЕННЫЙ ПАРОЛЬ ДЛЯ ВЗАИМОДЕЙСТВИЯ ПРИЛОЖЕНИЙ С ПОЧТОЙ)
email.from=your-email@gmail.com
mail.smtp.host=smtp.gmail.com
mail.smtp.port=587
mail.smtp.auth=true
mail.smtp.starttls.enable=true
```

**sms.properties:**
```properties
smpp.host=localhost
smpp.port=2775
smpp.system_id=smppclient1
smpp.password=password
smpp.system_type=OTP
smpp.source_addr=OTPService
```

### 3. Запуск SMPPSim (для тестирования SMS)

1. Скачайте [SMPPSim](https://github.com/delhee/SMPPSim/releases/tag/3.0.0)
2. Распакуйте архив и запустите:

```
startsmppsim.bat
```

### 4. Запуск приложения

Сервис будет доступен по адресу: `http://localhost:8080`

### 5. Тестирование API

Для тестирования API можно использовать Postman, cURL или файл `api-examples.http` в корне проекта.


## API документация

### 1. Публичные эндпоинты (No Authentication Required)

#### Регистрация пользователя
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "username",
  "password": "password"
}
```

#### Авторизация (Get JWT Token)
```http
POST /api/jwt/auth
Content-Type: application/json

{
  "username": "username",
  "password": "password"
}
```

### 2. Эндпоинты управления пользователем

#### Проверка статуса аутентификации
```http
GET /api/auth/status
Authorization: Bearer {jwt_token}
```

#### Получение профиля пользователя
```http
GET /api/auth/profile
Authorization: Bearer {jwt_token}
```

#### Смена пароля
```http
POST /api/auth/change-password
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "currentPassword": "currentPassword",
  "newPassword": "newPassword"
}
```

### 3. Операции с OTP

#### Отправка OTP по электронной почте
```http
POST /api/otp/send-email
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "email": "user@example.com"
}
```

#### Отправка OTP через SMS
```http
POST /api/otp/send-sms
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "phone": "+1234567890"
}
```

#### Верификация OTP для текущего пользователя
```http
POST /api/otp/verify
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "code": "123456"
}
```

#### Валидация OTP для указанного пользователя
```http
POST /api/otp/validate
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "username": "username",
  "code": "123456"
}
```

### 4. Операции экспорта OTP

#### Экспорт истории OTP текущего пользователя
```http
GET /api/otp/export/csv
Authorization: Bearer {jwt_token}
```

#### Экспорт истории OTP всех пользователей (только для администратора)
```http
GET /api/otp/export/admin/csv
Authorization: Bearer {admin_token}
```

#### Экспорт истории OTP конкретного пользователя (только для администратора)
```http
GET /api/otp/export/admin/csv/{username}
Authorization: Bearer {admin_token}
```

### 5. Интеграция с Telegram

#### Генерация токена для связи с Telegram
```http
GET /api/telegram/generate-link-token
Authorization: Bearer {jwt_token}
```

#### Проверка статуса подключения Telegram
```http
GET /api/telegram/status
Authorization: Bearer {jwt_token}
```

#### Отправка OTP через Telegram
```http
POST /api/telegram/send-otp
Authorization: Bearer {jwt_token}
```

#### Верификация и связывание аккаунта с Telegram
```http
POST /api/telegram/verify-link
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "code": "link_token"
}
```

### 6. Административные функции

#### Получение списка пользователей (только для администратора)
```http
GET /api/admin/users
Authorization: Bearer {admin_token}
```

#### Получение конфигурации OTP (только для администратора)
```http
GET /api/admin/otp-config
Authorization: Bearer {admin_token}
```

#### Обновление конфигурации OTP (только для администратора)
```http
PUT /api/admin/otp-config
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "codeLength": 6,
  "lifetimeMinutes": 15
}
```

#### Удаление пользователя (только для администратора)
```http
DELETE /api/admin/users/{userId}
Authorization: Bearer {admin_token}
```

## Устранение типичных проблем

### Проблемы с базой данных
- **Ошибка подключения**: Проверьте, что PostgreSQL запущен и доступен
- **Ошибка авторизации**: Проверьте правильность пароля в application.properties
- **Таблицы не создаются**: Проверьте режим spring.jpa.hibernate.ddl-auto

### Проблемы с отправкой Email
- **Ошибка аутентификации**: Используйте пароль приложения для Gmail
- **Письмо не приходит**: Проверьте папку "Спам" и настройки фильтрации

### Проблемы с SMPPSim
- **Порт занят**: Проверьте, что порты 2775 и 88 свободны
- **Не запускается**: Проверьте установку Java

### Проблемы с Telegram-ботом
- **Бот не отвечает**: Проверьте правильность токена
- **Не удается связать**: Проверьте токен связывания

## Дополнительные рекомендации

### Безопасность
- Используйте HTTPS в production
- Регулярно меняйте секретный ключ для JWT
- Не используйте пароли по умолчанию в production

### Производительность
- Для высоких нагрузок используйте Redis для хранения токенов и OTP кодов
- Оптимизируйте пул соединений с базой данных
