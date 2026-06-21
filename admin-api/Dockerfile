FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml ./common/pom.xml
COPY admin-api/pom.xml ./admin-api/pom.xml
COPY gateway-core/pom.xml ./gateway-core/pom.xml

RUN mvn dependency:go-offline -pl admin-api -am

COPY common/src ./common/src
COPY admin-api/src ./admin-api/src

RUN mvn clean package -pl admin-api -am -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

COPY --from=builder /app/admin-api/target/admin-api-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
