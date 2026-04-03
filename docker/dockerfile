FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY gradlew .
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew --version

COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

COPY . .

CMD ["./gradlew", "bootRun", "--no-daemon"]
