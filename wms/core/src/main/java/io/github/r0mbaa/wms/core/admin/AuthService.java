package io.github.r0mbaa.wms.core.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход и выдача токенов (NFR-SEC-01): короткоживущий JWT доступа и токен обновления, который
 * при каждом обмене заменяется новым.
 */
@Service
public class AuthService {

    static final String ISSUER = "wms-core";
    static final String ROLES_CLAIM = "roles";

    private static final String INVALID_CREDENTIALS = "Неверное имя пользователя или пароль";
    private static final String INVALID_REFRESH = "Сессия истекла или была завершена: войдите заново";

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** Сравнение с ним уравнивает время ответа для существующего и несуществующего имени. */
    private final String dummyHash;

    AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
    }

    @Transactional
    public TokenPair login(String username, String password) {
        AppUser user = users.findByUsername(UserService.normalize(username)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash()) || !user.isEnabled()) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        return issue(user);
    }

    /**
     * Обменивает токен обновления на новую пару. Повторное предъявление уже обменянного токена
     * означает, что его копия у кого-то ещё, поэтому отзываются все сессии пользователя.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenPair refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new BadCredentialsException(INVALID_REFRESH));
        AppUser user = stored.getUser();
        if (stored.isRevoked()) {
            refreshTokens.revokeAll(user, now);
            throw new BadCredentialsException(INVALID_REFRESH);
        }
        if (stored.isExpiredAt(now) || !user.isEnabled()) {
            throw new BadCredentialsException(INVALID_REFRESH);
        }
        stored.revoke(now);
        return issue(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(hash(refreshToken)).ifPresent(token -> token.revoke(clock.instant()));
    }

    /** Выдача токенов пользователю, уже прошедшему проверку другим способом: бейдж и PIN сборщика. */
    @Transactional
    public TokenPair issueTokens(String username) {
        AppUser user = users.findByUsername(username)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));
        return issue(user);
    }

    private TokenPair issue(AppUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(ROLES_CLAIM, user.getRoles().stream().map(Role::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        refreshTokens.save(new RefreshToken(user, hash(refreshToken), now, now.plus(properties.refreshTokenTtl())));

        return new TokenPair(accessToken, "Bearer", properties.accessTokenTtl().toSeconds(), refreshToken,
                user.getRoles().stream().sorted().toList());
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 обязан поддерживаться любой JVM", e);
        }
    }

    /**
     * @param expiresIn срок жизни токена доступа, с
     * @param roles     чтобы клиент сразу выбрал интерфейс (терминал или панель), не разбирая JWT
     */
    public record TokenPair(String accessToken, String tokenType, long expiresIn, String refreshToken, List<Role> roles) {
    }
}
