# Etapa de compilación
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -q -DskipTests package

# Etapa de ejecución
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S soundpass && adduser -S soundpass -G soundpass
WORKDIR /app
COPY --from=build /src/target/soundpass-backend-*.jar app.jar
USER soundpass
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
