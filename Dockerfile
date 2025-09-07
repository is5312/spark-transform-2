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

# Configure Spark Master REST API
RUN echo "spark.master.rest.enabled true" >> /opt/bitnami/spark/conf/spark-defaults.conf && \
    echo "spark.master.rest.port 6066" >> /opt/bitnami/spark/conf/spark-defaults.conf

# Create /opt/spark/jars directory and copy JARs there (expected by Spark job submission)
RUN mkdir -p /opt/spark/jars && \
    cp /opt/bitnami/spark/jars/dsl-library.jar /opt/spark/jars/ && \
    cp /opt/bitnami/spark/jars/spark-job.jar /opt/spark/jars/

# Set proper permissions
RUN chown -R 1001:1001 /opt/bitnami/spark/jars
RUN chown -R 1001:1001 /opt/spark/jars
RUN chown -R 1001:1001 /opt/data

# Switch back to spark user
USER 1001