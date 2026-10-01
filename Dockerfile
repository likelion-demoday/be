# 실행 전용 이미지. JAR은 이미지 밖에서 빌드해서 넣는다.
#
# 운영 배포는 deploy/push.sh 가 처리한다 (로컬에서 테스트·빌드 → JAR만 서버로 전송 → 서버에서 이 이미지 생성).
# 서버에서 Gradle 빌드를 돌리지 않는 이유: 2GB 서버에서 운영 중인 앱·DB와 메모리·CPU를 다투지 않기 위해서다.
#
# 직접 만들어 볼 때:
#   ./gradlew bootJar && cp build/libs/app.jar . && docker build -t resay-app .
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

COPY --chown=app:app app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
