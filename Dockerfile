# Stage 1: Build Stage
FROM gradle:8.10-jdk21 AS build

WORKDIR /app

# Gradle Wrapper와 build 파일들 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 의존성 다운로드
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 코드 복사
COPY src src

# 애플리케이션 빌드 (테스트 제외)
RUN ./gradlew clean bootJar -x test --no-daemon

# Stage 2: Runtime Stage
FROM eclipse-temurin:21-jdk-jammy

# 보안 및 최적화를 위한 non-root 사용자 생성
RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 소유권 변경
RUN chown -R spring:spring /app

# non-root 사용자로 전환
USER spring

# 헬스체크 설정 (optional, actuator 사용 시)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

# 애플리케이션 포트 노출
EXPOSE 8080

# JVM 옵션 및 실행
ENTRYPOINT ["java", \
    "-Duser.timezone=Asia/Seoul", \
    "-Dfile.encoding=UTF-8", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", \
    "app.jar"]
