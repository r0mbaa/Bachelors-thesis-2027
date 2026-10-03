package io.github.r0mbaa.wms.core.admin;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Аутентификация по JWT и ролевой доступ (FR-M13-01, NFR-SEC-01, NFR-SEC-02).
 *
 * <p>Токены выпускает и проверяет сам {@code core}, поэтому подпись симметричная (HS256):
 * внешнего сервера авторизации нет, и публиковать открытый ключ некому. Планировщик
 * пользовательских токенов не принимает, его вызывает только {@code core}.
 *
 * <p>Права на операции объявляются в контроллерах через {@code @PreAuthorize}, здесь только
 * граница «открыто или нужен вход».
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;

    SecurityConfig(SecurityProperties properties) {
        this.key = new SecretKeySpec(keyBytes(properties.jwtSecret()), "HmacSHA256");
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http
                // Токен передаётся в заголовке Authorization, cookie не используются, поэтому CSRF неприменим.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(AuthService.ISSUER));
        return decoder;
    }

    /** Роли лежат в claim {@code roles} без префикса; Spring Security ждёт {@code ROLE_}. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AuthService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** bcrypt с префиксом алгоритма в хэше: алгоритм можно сменить без миграции паролей (NFR-SEC-04). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static byte[] keyBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("wms.security.jwt-secret не задан: ключ подписи сгенерирован случайно, токены станут "
                    + "недействительны после перезапуска. Для постоянной среды задайте WMS_JWT_SECRET");
            byte[] random = new byte[MIN_KEY_BYTES];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = Base64.getDecoder().decode(secret.strip());
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("wms.security.jwt-secret короче " + MIN_KEY_BYTES
                    + " байт: HS256 требует ключ не короче длины хэша. Сгенерируйте, например: openssl rand -base64 32");
        }
        return bytes;
    }
}
