FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre AS run
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# 이 이미지는 배포용 산출물이므로 프로파일 기본값을 prod로 고정합니다.
# compose를 거치지 않고 docker run으로 띄워도 dev 설정이 적용되지 않습니다.
# docker-compose.yml의 environment 블록이 이 값을 덮어쓸 수 있습니다.
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
