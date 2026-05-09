FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
RUN apk add --no-cache bash
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties /src/
COPY gradle /src/gradle
RUN ./gradlew --no-daemon --version
COPY src /src/src
RUN ./gradlew --no-daemon shadowJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/build/libs/*-all.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
