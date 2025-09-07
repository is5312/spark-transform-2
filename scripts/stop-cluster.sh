#!/bin/bash

# Stop Spark Transform Cluster

set -e

echo "Stopping Spark Transform Cluster..."

# Stop the cluster
docker compose down

echo "Cluster stopped successfully!"

