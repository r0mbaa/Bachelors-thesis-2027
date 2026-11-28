# syntax=docker/dockerfile:1.7
# Образ сервиса core или planner: один файл на оба, модуль Gradle выбирает аргумент SERVICE.
#   docker build --build-arg SERVICE=core -t wms-core .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
ARG SERVICE
# Кэш Gradle переживает пересборку образа: зависимости не скачиваются каждый раз заново.
# sharing=locked: образы core и planner собираются параллельно, и второй ждёт, пока первый
# отпустит кэш, а не падает на блокировке Gradle.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew \
    && ./gradlew :${SERVICE}:bootJar --no-daemon --console=plain \
    && java -Djarmode=tools -jar ${SERVICE}/build/libs/${SERVICE}.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S wms && adduser -S wms -G wms
USER wms
WORKDIR /app
# Слои от редко меняющихся к часто меняющимся: при правке кода пересобирается только последний.
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
