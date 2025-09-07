FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the Spring Boot application JAR
COPY spring-boot-app/build/libs/spring-boot-app-*.jar app.jar

# Copy the DSL library and Spark job JARs
COPY dsl-library/build/libs/dsl-library.jar /app/libs/
COPY spark-job/build/libs/spark-job.jar /app/libs/

# Create directories for data and logs
RUN mkdir -p /opt/data /opt/logs /app/libs

# Expose port
EXPOSE 8080

# Set JVM options for large file processing and Spark compatibility
ENV JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]