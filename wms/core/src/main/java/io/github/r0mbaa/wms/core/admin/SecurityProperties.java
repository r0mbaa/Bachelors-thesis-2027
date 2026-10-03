package io.github.r0mbaa.wms.core.admin;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param jwtSecret       ключ подписи HS256 в Base64, не короче 32 байт. Если пуст, ключ генерируется
 *                        при запуске, и выданные токены не переживают перезапуск
 * @param accessTokenTtl  срок жизни токена доступа: короткий, отзыв не нужен (NFR-SEC-01)
 * @param refreshTokenTtl срок жизни токена обновления: примерно смена, чтобы терминал не просил
 *                        вход посреди работы
 * @param bootstrap       первый администратор для пустой БД
 */
@ConfigurationProperties("wms.security")
record SecurityProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("12h") Duration refreshTokenTtl,
        @DefaultValue Bootstrap bootstrap) {

    /**
     * @param password если пуст, администратор не создаётся
     */
    record Bootstrap(@DefaultValue("admin") String username, String password) {
    }
}
