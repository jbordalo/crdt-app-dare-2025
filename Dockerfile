# Use a lightweight OpenJDK image
FROM openjdk:21-slim-bullseye

# Set working directory inside the container
WORKDIR /app

# Copy your application JAR into the container
COPY target/crdt-dare-project-0.0.1.jar /app/app.jar

# Optional: make sure the JAR is executable
RUN chmod +x /app/app.jar

# Default command; overridden by docker-compose
CMD ["java", "-jar", "app.jar"]
