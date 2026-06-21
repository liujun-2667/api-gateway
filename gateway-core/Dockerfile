FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml ./common/pom.xml
COPY gateway-core/pom.xml ./gateway-core/pom.xml

RUN mvn dependency:go-offline -pl gateway-core -am

COPY common/src ./common/src
COPY gateway-core/src ./gateway-core/src

RUN mvn clean package -pl gateway-core -am -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

COPY --from=builder /app/gateway-core/target/gateway-core-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
