#!/bin/bash

# Start Spark Transform Cluster

set -e

echo "Starting Spark Transform Cluster..."

# Create necessary directories
mkdir -p data logs

# Start the cluster
docker compose up -d

echo "Cluster started successfully!"
echo ""
echo "Web UIs:"
echo "- Spark Master: http://localhost:8080"
echo "- Worker 1: http://localhost:8081"
echo "- Worker 2: http://localhost:8082"
echo "- Worker 3: http://localhost:8083"
echo "- History Server: http://localhost:18080"
echo "- Spring Boot API: http://localhost:8084"
echo ""
echo "To check cluster status:"
echo "docker compose ps"
echo ""
echo "To view logs:"
echo "docker compose logs -f"

