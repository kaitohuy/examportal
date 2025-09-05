# Bước 1: Build ứng dụng với Maven
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q -DskipTests package

# Bước 2: Run ứng dụng bằng JDK
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render sẽ inject biến PORT, ta expose nó
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
