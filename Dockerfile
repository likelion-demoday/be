# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 2GB 서버에서 빌드할 때 메모리 부족으로 죽지 않도록 Gradle 힙을 제한한다
ENV GRADLE_OPTS="-Xmx768m -Dorg.gradle.daemon=false"

# 의존성 먼저 받아서 소스만 바뀌었을 때는 캐시를 재사용한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 헬스체크용 curl, 한국 시간대
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*
ENV TZ=Asia/Seoul

# root가 아닌 사용자로 실행한다
RUN useradd --system --uid 1001 --create-home app \
    && mkdir -p /app/storage \
    && chown -R app:app /app
USER app

COPY --from=build --chown=app:app /workspace/build/libs/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
