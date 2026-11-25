plugins {
    id("wms.java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":layout"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security)
    // Проверка JWT на каждом запросе (NFR-SEC-01); токены выпускает сам core, внешнего IdP нет.
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    // OpenAPI 3.1 по коду контроллеров (NFR-M-07): /v3/api-docs и /swagger-ui.html.
    implementation(libs.springdoc.openapi.webmvc.ui)
    // Печать: этикетки с QR (FR-M1-12) и маршрутные листы (FR-M11-03).
    implementation(libs.pdfbox)
    implementation(libs.zxing.core)
    runtimeOnly(libs.dejavu.fonts)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    // bootRun сам поднимает PostgreSQL из docker-compose.yml; в bootJar не попадает.
    developmentOnly(platform(libs.spring.boot.bom))
    developmentOnly(libs.spring.boot.docker.compose)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    // Распознавание QR с отрисованной страницы PDF: этикетка проверяется так же, как её прочтёт сканер.
    testImplementation(libs.zxing.javase)
}
