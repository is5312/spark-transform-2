#!/bin/bash

# End-to-end test script for Spark Transform Cluster

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "🚀 Spark Transform Cluster - End-to-End Test"
echo "============================================="

# Function to print section headers
print_section() {
    echo ""
    echo -e "${BLUE}$1${NC}"
    echo "$(printf '=%.0s' {1..50})"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to wait for service to be ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_wait=${3:-60}
    local wait_time=0
    
    echo -n "Waiting for $service_name to be ready... "
    
    while [ $wait_time -lt $max_wait ]; do
        if curl -s --connect-timeout 5 "$url" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Ready${NC}"
            return 0
        fi
        sleep 2
        wait_time=$((wait_time + 2))
        echo -n "."
    done
    
    echo -e "${RED}✗ Timeout${NC}"
    return 1
}

print_section "Prerequisites Check"

# Check required tools
echo "Checking required tools..."
for tool in docker curl jq; do
    if command_exists "$tool"; then
        echo -e "  ${GREEN}✓${NC} $tool"
    else
        echo -e "  ${RED}✗${NC} $tool (required)"
        exit 1
    fi
done

# Check Java version
if command_exists java; then
    java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -ge 17 ]; then
        echo -e "  ${GREEN}✓${NC} Java $java_version"
    else
        echo -e "  ${YELLOW}⚠${NC} Java $java_version (recommend Java 17+)"
    fi
else
    echo -e "  ${YELLOW}⚠${NC} Java not found (will use Docker)"
fi

print_section "Build and Setup"

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean > /dev/null 2>&1 || true

# Build the project
echo "Building project with Gradle..."
if ./gradlew build -x test; then
    echo -e "${GREEN}✓ Build successful${NC}"
else
    echo -e "${RED}✗ Build failed${NC}"
    exit 1
fi

# Build Docker image
echo "Building Docker image..."
if docker build -t custom-spark:latest . > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Docker image built${NC}"
else
    echo -e "${RED}✗ Docker image build failed${NC}"
    exit 1
fi

print_section "Cluster Startup"

# Stop any existing containers
echo "Stopping existing containers..."
docker compose down > /dev/null 2>&1 || true

# Start the cluster
echo "Starting Spark cluster..."
if docker compose up -d; then
    echo -e "${GREEN}✓ Cluster started${NC}"
else
    echo -e "${RED}✗ Cluster startup failed${NC}"
    exit 1
fi

# Wait for services to be ready
echo "Waiting for services to be ready..."
wait_for_service "http://localhost:8080" "Spark Master" 120
wait_for_service "http://localhost:8084/api/transform/health" "Spring Boot API" 120

print_section "Health Check"

# Run health check
echo "Running cluster health check..."
if ./scripts/health-check.sh; then
    echo -e "${GREEN}✓ Cluster is healthy${NC}"
else
    echo -e "${YELLOW}⚠ Cluster has some issues${NC}"
fi

print_section "Test Data Preparation"

# Generate test data if needed
if [ ! -f "./test-data/sample-small.csv" ]; then
    echo "Test data not found, using existing samples..."
else
    echo "Test data found, copying to data directory..."
fi

# Ensure data directory exists
mkdir -p ./data
cp ./test-data/sample-small.csv ./data/ 2>/dev/null || true

print_section "API Testing"

# Run API tests
echo "Running API test suite..."
if ./scripts/test-api.sh; then
    echo -e "${GREEN}✓ API tests passed${NC}"
else
    echo -e "${YELLOW}⚠ Some API tests failed${NC}"
fi

print_section "Performance Test"

# Run a performance test with larger data
echo "Running performance test..."

# Generate medium-sized test data if it doesn't exist
if [ ! -f "./test-data/sample-medium.csv" ]; then
    echo "Generating medium test data..."
    cp ./test-data/sample-medium.csv ./data/ 2>/dev/null || true
fi

# Performance test DSL
perf_dsl='{
  "transformations": [
    {
      "target": "full_name",
      "operation": "concat",
      "sources": ["first_name", " ", "last_name"]
    },
    {
      "target": "email_upper",
      "operation": "uppercase",
      "source": "email"
    },
    {
      "target": "salary_bonus",
      "operation": "multiply",
      "sources": ["salary", "0.15"]
    },
    {
      "target": "total_compensation",
      "operation": "add",
      "sources": ["salary", "salary_bonus"]
    }
  ]
}'

perf_request='{
  "inputPath": "/opt/data/sample-medium.csv",
  "outputPath": "/opt/data/output/performance-test",
  "dslScript": "'"$(echo "$perf_dsl" | tr -d '\n' | sed 's/"/\\"/g')"'",
  "jobId": "perf-test-'$(date +%s)'"
}'

echo "Submitting performance test job..."
start_time=$(date +%s)

response=$(curl -s -X POST "http://localhost:8084/api/transform/submit" \
    -H "Content-Type: application/json" \
    -d "$perf_request")

if echo "$response" | jq -e '.status' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Performance test job submitted${NC}"
    
    # Wait for completion
    job_id=$(echo "$response" | jq -r '.jobId')
    echo "Waiting for performance test to complete..."
    
    # Simple wait loop
    for i in {1..30}; do
        status_response=$(curl -s "http://localhost:8084/api/transform/status/$job_id")
        status=$(echo "$status_response" | jq -r '.status' 2>/dev/null)
        
        if [ "$status" = "FINISHED" ] || [ "$status" = "SUCCESS" ]; then
            end_time=$(date +%s)
            duration=$((end_time - start_time))
            echo -e "${GREEN}✓ Performance test completed in ${duration}s${NC}"
            break
        elif [ "$status" = "FAILED" ] || [ "$status" = "ERROR" ]; then
            echo -e "${RED}✗ Performance test failed${NC}"
            break
        fi
        
        sleep 2
    done
else
    echo -e "${RED}✗ Performance test submission failed${NC}"
fi

print_section "Results Validation"

# Check output files
echo "Validating output files..."
if [ -d "./data/output" ]; then
    output_count=$(find ./data/output -name "*.csv" 2>/dev/null | wc -l)
    echo "Output files found: $output_count"
    
    if [ $output_count -gt 0 ]; then
        echo -e "${GREEN}✓ Transformations produced output files${NC}"
        
        # Show sample output
        echo "Sample output file:"
        find ./data/output -name "*.csv" | head -1 | xargs head -5
    else
        echo -e "${YELLOW}⚠ No output files found${NC}"
    fi
else
    echo -e "${RED}✗ No output directory found${NC}"
fi

print_section "Cleanup"

# Ask user if they want to keep the cluster running
echo "End-to-end test completed!"
echo ""
echo "Options:"
echo "1. Keep cluster running for further testing"
echo "2. Stop cluster and cleanup"
echo ""
read -p "Choose option (1 or 2): " choice

case $choice in
    1)
        echo -e "${GREEN}✓ Cluster kept running${NC}"
        echo "Access points:"
        echo "  - Spark Master: http://localhost:8080"
        echo "  - Spring Boot API: http://localhost:8084"
        echo "  - History Server: http://localhost:18080"
        ;;
    2)
        echo "Stopping cluster..."
        docker compose down
        echo -e "${GREEN}✓ Cluster stopped${NC}"
        ;;
    *)
        echo "Invalid choice, keeping cluster running"
        ;;
esac

echo ""
echo "🎉 End-to-End Test Complete!"
echo "============================"
