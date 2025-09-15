#!/bin/bash

# Spark Environment Configuration

# Java Configuration
export JAVA_HOME=/opt/bitnami/java
export PATH=$JAVA_HOME/bin:$PATH

# Spark Configuration
export SPARK_HOME=/opt/bitnami/spark
export SPARK_CONF_DIR=$SPARK_HOME/conf
export SPARK_LOG_DIR=/opt/spark/logs
export SPARK_WORKER_DIR=/opt/spark/work

# Worker Configuration
export SPARK_WORKER_WEBUI_PORT=8081
export SPARK_WORKER_PORT=7078

# Master Configuration
export SPARK_MASTER_WEBUI_PORT=8080
export SPARK_MASTER_PORT=7077

# History Server Configuration
export SPARK_HISTORY_OPTS="-Dspark.history.fs.logDirectory=/opt/spark/events"

# Logging Configuration
export SPARK_DAEMON_JAVA_OPTS="-Dlog4j.configurationFile=/opt/bitnami/spark/conf/log4j2.properties"

# Memory Configuration
export SPARK_DAEMON_MEMORY=1g

# Network Configuration
export SPARK_LOCAL_IP=0.0.0.0

# Application JAR Path
export SPARK_APP_JAR=/opt/spark/apps/spark-transform-1.0.0.jar

# Data Directories
export SPARK_DATA_DIR=/opt/spark/data
export SPARK_EVENTS_DIR=/opt/spark/events
export SPARK_WAREHOUSE_DIR=/opt/spark/warehouse

# Create directories if they don't exist
mkdir -p $SPARK_LOG_DIR
mkdir -p $SPARK_WORKER_DIR
mkdir -p $SPARK_DATA_DIR/input
mkdir -p $SPARK_DATA_DIR/output
mkdir -p $SPARK_DATA_DIR/shared
mkdir -p $SPARK_EVENTS_DIR
mkdir -p $SPARK_WAREHOUSE_DIR
