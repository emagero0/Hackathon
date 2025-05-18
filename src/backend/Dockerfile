# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the final image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Set timezone for the container
ENV TZ=Africa/Nairobi
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Set environment variables with default empty values
# These will be overridden by Docker run/compose environment variables
ENV DB_USERNAME=""
ENV DB_PASSWORD=""
ENV DYNAMICS_BC_USERNAME=""
ENV DYNAMICS_BC_API_KEY=""
ENV RABBITMQ_USERNAME=""
ENV RABBITMQ_PASSWORD=""

COPY --from=builder /app/target/ai-erp-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
