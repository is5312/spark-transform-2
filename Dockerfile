# Use Bitnami Spark base image
FROM bitnami/spark:3.5.2

# Switch to root to install dependencies
USER root

# Keep existing Java (17) - will handle SerializedLambda with JVM options

# Create data directory
RUN mkdir -p /opt/data

# Copy the DSL library JAR to the Spark jars directory
COPY dsl-library/build/libs/dsl-library.jar /opt/bitnami/spark/jars/

# Copy Spark job JAR
COPY spark-job/build/libs/spark-job.jar /opt/bitnami/spark/jars/

# Set proper permissions
RUN chown -R 1001:1001 /opt/bitnami/spark/jars
RUN chown -R 1001:1001 /opt/data

# Switch back to spark user
USER 1001