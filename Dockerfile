# Bước 1: Build ứng dụng với Maven
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -q

# Bước 2: Run ứng dụng với JRE Alpine (nhẹ hơn)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Xmx400m", \
    "-Xms128m", \
    "-XX:+UseSerialGC", \
    "-XX:MaxMetaspaceSize=128m", \
    "-XX:+TieredCompilation", \
    "-XX:TieredStopAtLevel=1", \
    "-Xss256k", \
    "-XX:+UseStringDeduplication", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]