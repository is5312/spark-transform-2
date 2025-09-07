#!/bin/bash

# Build script for Spark Transform Cluster

set -e

echo "Building Spark Transform Cluster..."

# Clean and build all modules
echo "Building Gradle modules..."
./gradlew clean build -x test

# Build Docker image
echo "Building Docker image..."
docker build -t custom-spark:latest .

echo "Build completed successfully!"
echo ""
echo "To start the cluster, run:"
echo "docker-compose up -d"
echo ""
echo "To check cluster status, run:"
echo "docker-compose ps"
