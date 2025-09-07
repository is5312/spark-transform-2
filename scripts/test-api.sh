#!/bin/bash

# Comprehensive API test script for Spark Transform Cluster

set -e

API_BASE="http://localhost:8084/api/transform"
TEST_DATA_DIR="./test-data"
OUTPUT_DIR="./data/output"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "🧪 Spark Transform API Test Suite"
echo "================================="

# Function to make API calls with error handling
api_call() {
    local method=$1
    local endpoint=$2
    local data=$3
    local description=$4
    
    echo -n "Testing $description... "
    
    if [ -n "$data" ]; then
        response=$(curl -s -X "$method" "$API_BASE$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null)
    else
        response=$(curl -s -X "$method" "$API_BASE$endpoint" 2>/dev/null)
    fi
    
    if [ $? -eq 0 ] && [ -n "$response" ]; then
        echo -e "${GREEN}✓ Success${NC}"
        echo "$response" | jq . 2>/dev/null || echo "$response"
        return 0
    else
        echo -e "${RED}✗ Failed${NC}"
        return 1
    fi
}

# Function to wait for job completion
wait_for_job() {
    local job_id=$1
    local max_wait=${2:-60}  # Default 60 seconds
    local wait_time=0
    
    echo "Waiting for job $job_id to complete..."
    
    while [ $wait_time -lt $max_wait ]; do
        response=$(curl -s "$API_BASE/status/$job_id" 2>/dev/null)
        if [ $? -eq 0 ]; then
            status=$(echo "$response" | jq -r '.status' 2>/dev/null)
            case $status in
                "FINISHED"|"SUCCESS")
                    echo -e "${GREEN}✓ Job completed successfully${NC}"
                    return 0
                    ;;
                "FAILED"|"ERROR")
                    echo -e "${RED}✗ Job failed${NC}"
                    echo "$response" | jq . 2>/dev/null || echo "$response"
                    return 1
                    ;;
                "RUNNING"|"SUBMITTED")
                    echo -n "."
                    sleep 2
                    wait_time=$((wait_time + 2))
                    ;;
                *)
                    echo -e "${YELLOW}⚠ Unknown status: $status${NC}"
                    sleep 2
                    wait_time=$((wait_time + 2))
                    ;;
            esac
        else
            echo -e "${RED}✗ Failed to get job status${NC}"
            return 1
        fi
    done
    
    echo -e "${YELLOW}⚠ Job did not complete within ${max_wait}s${NC}"
    return 1
}

echo ""
echo "1️⃣ Health Check Tests"
echo "-------------------"

# Test health endpoint
api_call "GET" "/health" "" "Health endpoint"

echo ""
echo "2️⃣ Job Management Tests"
echo "---------------------"

# Test listing jobs (should be empty initially)
api_call "GET" "/jobs" "" "List running jobs"

echo ""
echo "3️⃣ CSV Transformation Tests"
echo "-------------------------"

# Ensure test data exists
if [ ! -f "$TEST_DATA_DIR/sample-small.csv" ]; then
    echo -e "${RED}✗ Test data not found. Please run generate-test-data.sh first${NC}"
    exit 1
fi

# Copy test data to data directory
mkdir -p ./data
cp "$TEST_DATA_DIR/sample-small.csv" ./data/

# Test 1: Simple transformation
echo ""
echo "Test 1: Simple name concatenation"
simple_dsl='{
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

simple_request='{
  "inputPath": "/opt/data/sample-small.csv",
  "outputPath": "/opt/data/output/simple-test",
  "dslScript": "'"$(echo "$simple_dsl" | tr -d '\n' | sed 's/"/\\"/g')"'",
  "jobId": "test-simple-'$(date +%s)'"
}'

api_call "POST" "/submit" "$simple_request" "Simple transformation job"

# Extract job ID for monitoring
job_id=$(echo "$simple_request" | jq -r '.jobId')
if [ -n "$job_id" ] && [ "$job_id" != "null" ]; then
    wait_for_job "$job_id" 30
fi

# Test 2: Complex transformation
echo ""
echo "Test 2: Complex salary calculation"
complex_dsl='{
  "transformations": [
    {
      "target": "full_name",
      "operation": "concat",
      "sources": ["first_name", " ", "last_name"]
    },
    {
      "target": "annual_bonus",
      "operation": "multiply",
      "sources": ["salary", "0.1"]
    },
    {
      "target": "total_compensation",
      "operation": "add",
      "sources": ["salary", "annual_bonus"]
    },
    {
      "target": "department_upper",
      "operation": "uppercase",
      "source": "department"
    },
    {
      "target": "is_engineer",
      "operation": "conditional",
      "condition": "equals",
      "source": "department",
      "expected": "Engineering"
    }
  ]
}'

complex_request='{
  "inputPath": "/opt/data/sample-small.csv",
  "outputPath": "/opt/data/output/complex-test",
  "dslScript": "'"$(echo "$complex_dsl" | tr -d '\n' | sed 's/"/\\"/g')"'",
  "jobId": "test-complex-'$(date +%s)'"
}'

api_call "POST" "/submit" "$complex_request" "Complex transformation job"

# Extract job ID for monitoring
job_id=$(echo "$complex_request" | jq -r '.jobId')
if [ -n "$job_id" ] && [ "$job_id" != "null" ]; then
    wait_for_job "$job_id" 30
fi

echo ""
echo "4️⃣ Error Handling Tests"
echo "---------------------"

# Test invalid DSL
echo ""
echo "Test 3: Invalid DSL script"
invalid_request='{
  "inputPath": "/opt/data/sample-small.csv",
  "outputPath": "/opt/data/output/invalid-test",
  "dslScript": "invalid json",
  "jobId": "test-invalid-'$(date +%s)'"
}'

api_call "POST" "/submit" "$invalid_request" "Invalid DSL script (should fail gracefully)"

# Test non-existent file
echo ""
echo "Test 4: Non-existent input file"
nonexistent_request='{
  "inputPath": "/opt/data/nonexistent.csv",
  "outputPath": "/opt/data/output/nonexistent-test",
  "dslScript": "'"$(echo "$simple_dsl" | tr -d '\n' | sed 's/"/\\"/g')"'",
  "jobId": "test-nonexistent-'$(date +%s)'"
}'

api_call "POST" "/submit" "$nonexistent_request" "Non-existent input file (should fail gracefully)"

echo ""
echo "5️⃣ Job Status Tests"
echo "-----------------"

# Test job status endpoint
api_call "GET" "/status/test-simple-$(date +%s)" "" "Job status for non-existent job"

echo ""
echo "6️⃣ Final Job List"
echo "---------------"

# List all running jobs
api_call "GET" "/jobs" "" "Final job list"

echo ""
echo "📊 Test Results Summary"
echo "====================="

# Check if output files were created
if [ -d "./data/output" ]; then
    output_files=$(find ./data/output -name "*.csv" 2>/dev/null | wc -l)
    echo "Output files created: $output_files"
    
    if [ $output_files -gt 0 ]; then
        echo -e "${GREEN}✓ Transformations completed successfully${NC}"
        echo "Output directory contents:"
        find ./data/output -type f -name "*.csv" -exec ls -lh {} \;
    else
        echo -e "${YELLOW}⚠ No output files found${NC}"
    fi
else
    echo -e "${RED}✗ No output directory found${NC}"
fi

echo ""
echo "🎯 Test Suite Completed!"
echo "======================"
