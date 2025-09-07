#!/bin/bash

# Quick test script for Spark Transform Cluster

set -e

echo "⚡ Quick Test - Spark Transform Cluster"
echo "======================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if cluster is running
check_cluster() {
    echo -n "Checking if cluster is running... "
    
    if curl -s --connect-timeout 5 "http://localhost:8084/api/transform/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Running${NC}"
        return 0
    else
        echo -e "${RED}✗ Not running${NC}"
        return 1
    fi
}

# Function to run a simple transformation test
run_simple_test() {
    echo "Running simple transformation test..."
    
    # Ensure test data exists
    if [ ! -f "./test-data/sample-small.csv" ]; then
        echo -e "${RED}✗ Test data not found${NC}"
        return 1
    fi
    
    # Copy test data to data directory
    mkdir -p ./data
    cp ./test-data/sample-small.csv ./data/
    
    # Simple DSL for testing
    local dsl='{
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
        }
      ]
    }'
    
    local request='{
      "inputPath": "/opt/data/sample-small.csv",
      "outputPath": "/opt/data/output/quick-test",
      "dslScript": "'"$(echo "$dsl" | tr -d '\n' | sed 's/"/\\"/g')"'",
      "jobId": "quick-test-'$(date +%s)'"
    }'
    
    echo "Submitting test job..."
    local response=$(curl -s -X POST "http://localhost:8084/api/transform/submit" \
        -H "Content-Type: application/json" \
        -d "$request")
    
    if echo "$response" | jq -e '.status' > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Job submitted successfully${NC}"
        
        # Wait for completion (simplified)
        local job_id=$(echo "$response" | jq -r '.jobId')
        echo "Waiting for job completion..."
        
        for i in {1..15}; do
            local status_response=$(curl -s "http://localhost:8084/api/transform/status/$job_id")
            local status=$(echo "$status_response" | jq -r '.status' 2>/dev/null)
            
            if [ "$status" = "FINISHED" ] || [ "$status" = "SUCCESS" ]; then
                echo -e "${GREEN}✓ Job completed successfully${NC}"
                return 0
            elif [ "$status" = "FAILED" ] || [ "$status" = "ERROR" ]; then
                echo -e "${RED}✗ Job failed${NC}"
                return 1
            fi
            
            sleep 2
        done
        
        echo -e "${YELLOW}⚠ Job did not complete within 30 seconds${NC}"
        return 1
    else
        echo -e "${RED}✗ Job submission failed${NC}"
        return 1
    fi
}

# Main execution
echo "Starting quick test..."

# Check if cluster is running
if ! check_cluster; then
    echo ""
    echo "Cluster is not running. Please start it first:"
    echo "  ./scripts/start-cluster.sh"
    exit 1
fi

# Run simple test
if run_simple_test; then
    echo ""
    echo -e "${GREEN}🎉 Quick test passed!${NC}"
    echo "The cluster is working correctly."
    exit 0
else
    echo ""
    echo -e "${RED}❌ Quick test failed!${NC}"
    echo "Please check the cluster status and logs."
    exit 1
fi
