#!/bin/bash

# Performance test script for Spark Transform Cluster

set -e

API_BASE="http://localhost:8084/api/transform"
DATA_DIR="./data"
TEST_SIZES=(10 50 100)  # MB

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "⚡ Spark Transform Cluster - Performance Test"
echo "============================================="

# Function to generate test data of specific size
generate_test_data() {
    local size_mb=$1
    local filename="perf-test-${size_mb}mb.csv"
    local filepath="$DATA_DIR/$filename"
    
    echo "Generating ${size_mb}MB test data..."
    
    # Header
    echo "id,product_name,category,price,quantity,customer_id,customer_name,customer_email,order_date,region,status,priority,description" > "$filepath"
    
    # Generate data
    counter=1
    while [ $(du -m "$filepath" 2>/dev/null | cut -f1) -lt $size_mb ]; do
        # Generate random data
        product_id=$((counter % 1000 + 1))
        product_name="Product_${product_id}_$(date +%s)"
        category=$((counter % 5))
        case $category in
            0) category="Electronics" ;;
            1) category="Furniture" ;;
            2) category="Clothing" ;;
            3) category="Books" ;;
            4) category="Sports" ;;
        esac
        
        price=$(echo "scale=2; $RANDOM/100 + 10" | bc)
        quantity=$((RANDOM % 10 + 1))
        customer_id=$((RANDOM % 10000 + 1))
        customer_name="Customer_${customer_id}"
        customer_email="customer${customer_id}@email.com"
        order_date="2024-$(printf "%02d" $((RANDOM % 12 + 1)))-$(printf "%02d" $((RANDOM % 28 + 1)))"
        region=$((RANDOM % 4))
        case $region in
            0) region="North" ;;
            1) region="South" ;;
            2) region="East" ;;
            3) region="West" ;;
        esac
        status=$((RANDOM % 3))
        case $status in
            0) status="Pending" ;;
            1) status="Shipped" ;;
            2) status="Delivered" ;;
        esac
        priority=$((RANDOM % 3))
        case $priority in
            0) priority="Low" ;;
            1) priority="Medium" ;;
            2) priority="High" ;;
        esac
        description="This is a test description for product ${product_id} with some additional text to make the record larger for performance testing purposes."
        
        echo "$counter,$product_name,$category,$price,$quantity,$customer_id,$customer_name,$customer_email,$order_date,$region,$status,$priority,$description" >> "$filepath"
        
        counter=$((counter + 1))
        
        # Progress indicator
        if [ $((counter % 5000)) -eq 0 ]; then
            current_size=$(du -m "$filepath" 2>/dev/null | cut -f1)
            echo "  Generated $counter records, current size: ${current_size}MB"
        fi
    done
    
    final_size=$(du -m "$filepath" 2>/dev/null | cut -f1)
    record_count=$(wc -l < "$filepath")
    echo "  Generated $filename: ${record_count} records, ${final_size}MB"
}

# Function to run performance test
run_performance_test() {
    local size_mb=$1
    local filename="perf-test-${size_mb}mb.csv"
    local job_id="perf-test-${size_mb}mb-$(date +%s)"
    
    echo ""
    echo -e "${BLUE}Testing ${size_mb}MB file${NC}"
    echo "------------------------"
    
    # Complex DSL for performance testing
    local dsl='{
      "transformations": [
        {
          "target": "full_customer_name",
          "operation": "concat",
          "sources": ["customer_name", " (ID: ", "customer_id", ")"]
        },
        {
          "target": "email_upper",
          "operation": "uppercase",
          "source": "customer_email"
        },
        {
          "target": "total_price",
          "operation": "multiply",
          "sources": ["price", "quantity"]
        },
        {
          "target": "discount_amount",
          "operation": "multiply",
          "sources": ["total_price", "0.1"]
        },
        {
          "target": "final_price",
          "operation": "add",
          "sources": ["total_price", "-", "discount_amount"]
        },
        {
          "target": "category_upper",
          "operation": "uppercase",
          "source": "category"
        },
        {
          "target": "region_upper",
          "operation": "uppercase",
          "source": "region"
        },
        {
          "target": "is_high_priority",
          "operation": "conditional",
          "condition": "equals",
          "source": "priority",
          "expected": "High"
        },
        {
          "target": "processing_timestamp",
          "operation": "constant",
          "value": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"
        }
      ]
    }'
    
    local request='{
      "inputPath": "/opt/data/'$filename'",
      "outputPath": "/opt/data/output/performance-'$size_mb'mb",
      "dslScript": "'"$(echo "$dsl" | tr -d '\n' | sed 's/"/\\"/g')"'",
      "jobId": "'$job_id'"
    }'
    
    echo "Submitting job..."
    local start_time=$(date +%s)
    
    local response=$(curl -s -X POST "$API_BASE/submit" \
        -H "Content-Type: application/json" \
        -d "$request")
    
    if echo "$response" | jq -e '.status' > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Job submitted successfully${NC}"
        
        # Monitor job progress
        echo "Monitoring job progress..."
        local last_status=""
        local check_count=0
        local max_checks=300  # 10 minutes max
        
        while [ $check_count -lt $max_checks ]; do
            local status_response=$(curl -s "$API_BASE/status/$job_id")
            local status=$(echo "$status_response" | jq -r '.status' 2>/dev/null)
            
            if [ "$status" != "$last_status" ]; then
                echo "  Status: $status"
                last_status="$status"
            fi
            
            case $status in
                "FINISHED"|"SUCCESS")
                    local end_time=$(date +%s)
                    local duration=$((end_time - start_time))
                    local records_per_second=$((record_count / duration))
                    local mb_per_second=$((size_mb / duration))
                    
                    echo -e "${GREEN}✓ Job completed successfully${NC}"
                    echo "  Duration: ${duration}s"
                    echo "  Records processed: $record_count"
                    echo "  Records/second: $records_per_second"
                    echo "  MB/second: $mb_per_second"
                    
                    # Store results
                    echo "$size_mb,$duration,$record_count,$records_per_second,$mb_per_second" >> "$DATA_DIR/performance-results.csv"
                    return 0
                    ;;
                "FAILED"|"ERROR")
                    echo -e "${RED}✗ Job failed${NC}"
                    echo "$status_response" | jq . 2>/dev/null || echo "$status_response"
                    return 1
                    ;;
                "RUNNING"|"SUBMITTED")
                    sleep 2
                    check_count=$((check_count + 1))
                    ;;
                *)
                    echo "  Unknown status: $status"
                    sleep 2
                    check_count=$((check_count + 1))
                    ;;
            esac
        done
        
        echo -e "${YELLOW}⚠ Job did not complete within 10 minutes${NC}"
        return 1
        
    else
        echo -e "${RED}✗ Job submission failed${NC}"
        echo "$response" | jq . 2>/dev/null || echo "$response"
        return 1
    fi
}

# Function to check cluster health before testing
check_cluster_health() {
    echo "Checking cluster health..."
    
    if ! curl -s --connect-timeout 5 "http://localhost:8084/api/transform/health" > /dev/null 2>&1; then
        echo -e "${RED}✗ Spring Boot API not accessible${NC}"
        return 1
    fi
    
    if ! curl -s --connect-timeout 5 "http://localhost:8080" > /dev/null 2>&1; then
        echo -e "${RED}✗ Spark Master not accessible${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✓ Cluster is healthy${NC}"
    return 0
}

# Function to generate performance report
generate_report() {
    local report_file="$DATA_DIR/performance-report.txt"
    
    echo "Generating performance report..."
    
    cat > "$report_file" << EOF
Spark Transform Cluster - Performance Test Report
================================================
Test Date: $(date)
Cluster Configuration:
- 1 Master Node
- 3 Worker Nodes
- Spring Boot API

Test Results:
EOF
    
    if [ -f "$DATA_DIR/performance-results.csv" ]; then
        echo "File Size (MB),Duration (s),Records,Records/s,MB/s" >> "$report_file"
        cat "$DATA_DIR/performance-results.csv" >> "$report_file"
        
        echo ""
        echo "Summary:" >> "$report_file"
        
        # Calculate averages
        local total_tests=$(wc -l < "$DATA_DIR/performance-results.csv")
        local avg_duration=$(awk -F',' '{sum+=$2} END {print sum/NR}' "$DATA_DIR/performance-results.csv")
        local avg_records_per_sec=$(awk -F',' '{sum+=$4} END {print sum/NR}' "$DATA_DIR/performance-results.csv")
        local avg_mb_per_sec=$(awk -F',' '{sum+=$5} END {print sum/NR}' "$DATA_DIR/performance-results.csv")
        
        echo "- Total tests: $total_tests" >> "$report_file"
        echo "- Average duration: ${avg_duration}s" >> "$report_file"
        echo "- Average records/second: ${avg_records_per_sec}" >> "$report_file"
        echo "- Average MB/second: ${avg_mb_per_sec}" >> "$report_file"
    else
        echo "No performance data available" >> "$report_file"
    fi
    
    echo "Report saved to: $report_file"
}

# Main execution
echo "Starting performance tests..."

# Check cluster health
if ! check_cluster_health; then
    echo -e "${RED}✗ Cluster health check failed. Please ensure the cluster is running.${NC}"
    exit 1
fi

# Create data directory
mkdir -p "$DATA_DIR"

# Initialize results file
echo "File Size (MB),Duration (s),Records,Records/s,MB/s" > "$DATA_DIR/performance-results.csv"

# Run tests for each size
for size in "${TEST_SIZES[@]}"; do
    echo ""
    echo -e "${BLUE}=== Testing ${size}MB File ===${NC}"
    
    # Generate test data
    generate_test_data $size
    
    # Run performance test
    if run_performance_test $size; then
        echo -e "${GREEN}✓ ${size}MB test completed${NC}"
    else
        echo -e "${RED}✗ ${size}MB test failed${NC}"
    fi
    
    # Wait between tests
    echo "Waiting 30 seconds before next test..."
    sleep 30
done

# Generate final report
generate_report

echo ""
echo "🎯 Performance Test Complete!"
echo "============================"
echo "Results saved to: $DATA_DIR/performance-results.csv"
echo "Report saved to: $DATA_DIR/performance-report.txt"
