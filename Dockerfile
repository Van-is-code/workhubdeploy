# Stage 1: Build the app
FROM eclipse-temurin:17-jdk-alpine as builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (cache dependencies)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# ✅ FIX: Cấp quyền thực thi cho mvnw
RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline

# Copy the rest of the source
COPY src src

# Build the app
RUN ./mvnw package -DskipTests

# Stage 2: Run the app
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE ${PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]
