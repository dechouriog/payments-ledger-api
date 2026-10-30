# Etapa 1: compila con Maven (no necesitas Maven instalado)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Etapa 2: imagen liviana solo con el JRE y el jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/payments-ledger-api-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
