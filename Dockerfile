# syntax=docker/dockerfile:1

# ---- 1) 빌드: Gradle로 bootJar를 만들고 레이어별로 푼다 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 캐시: 소스만 바뀌면 의존성 다운로드를 건너뛴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon -q -x test \
 && java -Djarmode=tools -jar build/libs/eventcart-*.jar extract --layers --launcher --destination extracted

# ---- 2) 실행: JRE만, root가 아닌 사용자로 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app

# 잘 안 바뀌는 레이어부터 복사 → 코드만 바뀌면 마지막 레이어만 새로 받는다
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080 8081
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
