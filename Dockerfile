# ---- 빌드 단계: JDK와 Gradle은 이 단계에만 있고 실행 이미지에는 들어가지 않는다 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 정의만 먼저 복사해서 받아 두면, 소스만 바뀔 때 이 레이어는 캐시를 쓴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- 실행 단계: JRE만 사용, root가 아닌 사용자로 실행 ----
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar app.jar
USER app

EXPOSE 8080
# 컨테이너 메모리 제한의 75%까지 힙으로 사용
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
