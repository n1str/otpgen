package ru.n1str.otp.dao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.n1str.otp.models.OtpCode;
import ru.n1str.otp.models.OtpStatus;
import ru.n1str.otp.models.User;
import ru.n1str.otp.repository.UserRepository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Slf4j
public class OtpCodeJdbcDao {
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final RowMapper<OtpCode> otpCodeRowMapper;

    // SQL-запросы для работы с таблицей otp_code
    private static final String INSERT_OTP =
            "INSERT INTO otp_code (code, status, created_at, expires_at, user_id, operation_id, channel) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_STATUS =
            "UPDATE otp_code SET status = ? WHERE id = ?";
    private static final String FIND_ACTIVE_BY_USER_CODE =
            "SELECT * FROM otp_code WHERE user_id = ? AND code = ? AND status = 'ACTIVE'";
    private static final String FIND_BY_USER_AND_STATUS =
            "SELECT * FROM otp_code WHERE user_id = ? AND status = ?";
    private static final String FIND_EXPIRED_ACTIVE =
            "SELECT * FROM otp_code WHERE status = 'ACTIVE' AND expires_at < ?";
    private static final String DELETE_BY_USER_ID =
            "DELETE FROM otp_code WHERE user_id = ?";

    @Autowired
    public OtpCodeJdbcDao(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;

        this.otpCodeRowMapper = (rs, rowNum) -> {
            OtpCode otpCode = new OtpCode();
            otpCode.setId(rs.getLong("id"));
            otpCode.setCode(rs.getString("code"));
            otpCode.setStatus(OtpStatus.valueOf(rs.getString("status")));
            otpCode.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            otpCode.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
            otpCode.setOperationId(rs.getString("operation_id"));

            Long userId = rs.getLong("user_id");
            User user = userRepository.findById(userId).orElse(null);
            otpCode.setUser(user);

            String channelStr = rs.getString("channel");
            if (channelStr != null) {
                otpCode.setChannel(OtpCode.OtpChannel.valueOf(channelStr));
            }

            return otpCode;
        };
    }

    public OtpCode save(OtpCode otpCode) {
        if (otpCode.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(INSERT_OTP, new String[]{"id"});
                ps.setString(1, otpCode.getCode());
                ps.setString(2, otpCode.getStatus().name());
                ps.setTimestamp(3, Timestamp.valueOf(otpCode.getCreatedAt()));
                ps.setTimestamp(4, Timestamp.valueOf(otpCode.getExpiresAt()));
                ps.setLong(5, otpCode.getUser().getId());
                ps.setString(6, otpCode.getOperationId());

                if (otpCode.getChannel() != null) {
                    ps.setString(7, otpCode.getChannel().name());
                } else {
                    ps.setNull(7, Types.VARCHAR);
                }

                return ps;
            }, keyHolder);

            otpCode.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
            log.debug("Created new OTP code: {}", otpCode.getId());
        } else {
            jdbcTemplate.update(UPDATE_STATUS, otpCode.getStatus().name(), otpCode.getId());
            log.debug("Updated OTP code status: {}", otpCode.getId());
        }

        return otpCode;
    }

    public Optional<OtpCode> findByUserAndCodeAndStatus(User user, String code, OtpStatus status) {
        try {
            OtpCode otpCode = jdbcTemplate.queryForObject(
                    FIND_ACTIVE_BY_USER_CODE,
                    new Object[]{user.getId(), code},
                    otpCodeRowMapper
            );
            return Optional.ofNullable(otpCode);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<OtpCode> findByUserAndStatus(User user, OtpStatus status) {
        return jdbcTemplate.query(
                FIND_BY_USER_AND_STATUS,
                new Object[]{user.getId(), status.name()},
                otpCodeRowMapper
        );
    }

    public List<OtpCode> findExpiredActiveCodes() {
        return jdbcTemplate.query(
                FIND_EXPIRED_ACTIVE,
                new Object[]{Timestamp.valueOf(LocalDateTime.now())},
                otpCodeRowMapper
        );
    }

    public void deleteByUser(User user) {
        int count = jdbcTemplate.update(DELETE_BY_USER_ID, user.getId());
        log.debug("Deleted {} OTP codes for user: {}", count, user.getId());
    }
}