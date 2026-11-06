package io.github.r0mbaa.wms.core.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Описание API учётного ядра по OpenAPI 3.1 строится из кода контроллеров (NFR-M-07), поэтому
 * не расходится с реализацией. Схема Bearer позволяет пробовать запросы из Swagger UI с
 * токеном, полученным в {@code /api/v1/auth/login}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI coreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WMS core")
                        .description("Учётное ядро: топология, номенклатура, остатки, заказы, резервы, задания, "
                                + "терминал сборщика, отгрузка")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
