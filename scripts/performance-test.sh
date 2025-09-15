#!/bin/bash

# Spark Transform Performance Testing Script
# Comprehensive performance testing for the Spark cluster

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m'

# Configuration
API_URL="http://localhost:8084/api/transform"
DATA_DIR="./data"
TEST_RESULTS_DIR="./test-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_FILE="${TEST_RESULTS_DIR}/performance_test_${TIMESTAMP}.json"

# Test parameters
SMALL_DATASET_SIZE=1000
MEDIUM_DATASET_SIZE=50000
LARGE_DATASET_SIZE=500000

# Ensure directories exist
mkdir -p "$DATA_DIR" "$TEST_RESULTS_DIR"

# Logging functions
log() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[$(date +'%H:%M:%S')] WARNING:${NC} $1"
}

error() {
    echo -e "${RED}[$(date +'%H:%M:%S')] ERROR:${NC} $1"
}

info() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')] INFO:${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if cluster is running
    if ! curl -s http://localhost:8080 > /dev/null; then
        error "Spark Master is not accessible. Please start the cluster first."
        exit 1
    fi
    
    if ! curl -s http://localhost:8084/actuator/health > /dev/null; then
        error "Spring Boot API is not accessible. Please start the cluster first."
        exit 1
    fi
    
    # Check if jq is available for JSON processing
    if ! command -v jq &> /dev/null; then
        warn "jq is not installed. Some features may not work properly."
    fi
    
    log "✅ Prerequisites check passed"
}

# Function to generate test data
generate_test_data() {
    local size="$1"
    local filename="$2"
    local filepath="${DATA_DIR}/${filename}"
    
    log "Generating test data: $filename ($size rows)"
    
    # Create CSV header
    echo "id,first_name,last_name,email,age,salary,department,hire_date,active" > "$filepath"
    
    # Generate random data
    for ((i=1; i<=size; i++)); do
        local first_name="FirstName$i"
        local last_name="LastName$i"
        local email="user$i@company.com"
        local age=$((20 + RANDOM % 45))
        local salary=$((30000 + RANDOM % 70000))
        local dept_num=$((RANDOM % 5))
        local departments=("Engineering" "Sales" "Marketing" "HR" "Finance")
        local department="${departments[$dept_num]}"
        local hire_date="202$((RANDOM % 4))-$(printf "%02d" $((1 + RANDOM % 12)))-$(printf "%02d" $((1 + RANDOM % 28)))"
        local active=$((RANDOM % 2))
        
        echo "$i,$first_name,$last_name,$email,$age,$salary,$department,$hire_date,$active" >> "$filepath"
        
        # Show progress for large datasets
        if ((i % 10000 == 0)); then
            echo -n "."
        fi
    done
    
    echo ""
    log "✅ Generated $size rows in $filename"
}

# Function to create DSL transformation script
create_dsl_script() {
    local script_type="$1"
    
    case "$script_type" in
        "simple")
            echo '{
                "transformations": [
                    {
                        "target": "full_name",
                        "operation": "concat",
                        "sources": ["first_name", " ", "last_name"]
                    }
                ]
            }'
            ;;
        "complex")
            echo '{
                "transformations": [
                    {
                        "target": "full_name",
                        "operation": "concat",
                        "sources": ["first_name", " ", "last_name"]
                    },
                    {
                        "target": "email_domain",
                        "operation": "extract_domain",
                        "source": "email"
                    },
                    {
                        "target": "salary_grade",
                        "operation": "conditional",
                        "conditions": [
                            {"if": "salary >= 80000", "then": "Senior"},
                            {"if": "salary >= 50000", "then": "Mid"},
                            {"else": "Junior"}
                        ]
                    },
                    {
                        "target": "years_experience",
                        "operation": "calculate_years",
                        "source": "hire_date"
                    }
                ]
            }'
            ;;
        "intensive")
            echo '{
                "transformations": [
                    {
                        "target": "full_name",
                        "operation": "concat",
                        "sources": ["first_name", " ", "last_name"]
                    },
                    {
                        "target": "full_name_upper",
                        "operation": "uppercase",
                        "source": "full_name"
                    },
                    {
                        "target": "email_masked",
                        "operation": "mask_email",
                        "source": "email"
                    },
                    {
                        "target": "annual_salary",
                        "operation": "multiply",
                        "sources": ["salary", "12"]
                    },
                    {
                        "target": "age_group",
                        "operation": "conditional",
                        "conditions": [
                            {"if": "age < 30", "then": "Young"},
                            {"if": "age < 50", "then": "Middle"},
                            {"else": "Senior"}
                        ]
                    },
                    {
                        "target": "employee_code",
                        "operation": "concat",
                        "sources": ["department", "-", "id"]
                    }
                ]
            }'
            ;;
    esac
}

# Function to submit job and measure performance
submit_job() {
    local input_file="$1"
    local output_dir="$2"
    local dsl_script="$3"
    local job_id="$4"
    local test_name="$5"
    
    log "Submitting job: $test_name (ID: $job_id)"
    
    local start_time=$(date +%s.%N)
    
    # Submit the job
    local response=$(curl -s -X POST "$API_URL/submit" \
        -H "Content-Type: application/json" \
        -d "{
            \"inputPath\": \"/opt/data/$input_file\",
            \"outputPath\": \"/opt/data/$output_dir\",
            \"dslScript\": \"$(echo "$dsl_script" | jq -c . | sed 's/"/\\"/g')\",
            \"jobId\": \"$job_id\"
        }")
    
    if [[ $? -ne 0 ]]; then
        error "Failed to submit job: $job_id"
        return 1
    fi
    
    log "Job submitted successfully: $job_id"
    
    # Monitor job completion
    local job_completed=false
    local timeout=300  # 5 minutes timeout
    local elapsed=0
    
    while [[ $elapsed -lt $timeout ]]; do
        local status_response=$(curl -s "$API_URL/status/$job_id")
        local status=$(echo "$status_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
        
        case "$status" in
            "COMPLETED"|"SUCCEEDED")
                job_completed=true
                break
                ;;
            "FAILED"|"ERROR")
                error "Job failed: $job_id"
                return 1
                ;;
            "RUNNING"|"SUBMITTED")
                echo -n "."
                sleep 5
                elapsed=$((elapsed + 5))
                ;;
            *)
                warn "Unknown job status: $status"
                sleep 5
                elapsed=$((elapsed + 5))
                ;;
        esac
    done
    
    echo ""
    
    if [[ $job_completed == false ]]; then
        error "Job timed out: $job_id"
        return 1
    fi
    
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc -l)
    
    log "✅ Job completed: $job_id (Duration: ${duration}s)"
    
    # Store results
    echo "$duration"
}

# Function to run performance test suite
run_test_suite() {
    local test_results=()
    
    log "🚀 Starting Performance Test Suite"
    log "Results will be saved to: $RESULTS_FILE"
    
    # Initialize results file
    echo "{" > "$RESULTS_FILE"
    echo "  \"timestamp\": \"$(date -Iseconds)\"," >> "$RESULTS_FILE"
    echo "  \"tests\": [" >> "$RESULTS_FILE"
    
    # Test 1: Small dataset with simple transformation
    info "Test 1: Small dataset ($SMALL_DATASET_SIZE rows) with simple transformation"
    generate_test_data $SMALL_DATASET_SIZE "small_test.csv"
    local simple_dsl=$(create_dsl_script "simple")
    local duration1=$(submit_job "small_test.csv" "output_small_simple" "$simple_dsl" "test1_small_simple" "Small Simple")
    
    # Test 2: Small dataset with complex transformation
    info "Test 2: Small dataset ($SMALL_DATASET_SIZE rows) with complex transformation"
    local complex_dsl=$(create_dsl_script "complex")
    local duration2=$(submit_job "small_test.csv" "output_small_complex" "$complex_dsl" "test2_small_complex" "Small Complex")
    
    # Test 3: Medium dataset with simple transformation
    info "Test 3: Medium dataset ($MEDIUM_DATASET_SIZE rows) with simple transformation"
    generate_test_data $MEDIUM_DATASET_SIZE "medium_test.csv"
    local duration3=$(submit_job "medium_test.csv" "output_medium_simple" "$simple_dsl" "test3_medium_simple" "Medium Simple")
    
    # Test 4: Medium dataset with complex transformation
    info "Test 4: Medium dataset ($MEDIUM_DATASET_SIZE rows) with complex transformation"
    local duration4=$(submit_job "medium_test.csv" "output_medium_complex" "$complex_dsl" "test4_medium_complex" "Medium Complex")
    
    # Test 5: Large dataset with simple transformation
    info "Test 5: Large dataset ($LARGE_DATASET_SIZE rows) with simple transformation"
    generate_test_data $LARGE_DATASET_SIZE "large_test.csv"
    local duration5=$(submit_job "large_test.csv" "output_large_simple" "$simple_dsl" "test5_large_simple" "Large Simple")
    
    # Test 6: Large dataset with intensive transformation
    info "Test 6: Large dataset ($LARGE_DATASET_SIZE rows) with intensive transformation"
    local intensive_dsl=$(create_dsl_script "intensive")
    local duration6=$(submit_job "large_test.csv" "output_large_intensive" "$intensive_dsl" "test6_large_intensive" "Large Intensive")
    
    # Write results to JSON file
    cat >> "$RESULTS_FILE" << EOF
    {
      "name": "Small dataset, simple transformation",
      "dataset_size": $SMALL_DATASET_SIZE,
      "transformation_type": "simple",
      "duration_seconds": $duration1,
      "rows_per_second": $(echo "scale=2; $SMALL_DATASET_SIZE / $duration1" | bc -l)
    },
    {
      "name": "Small dataset, complex transformation",
      "dataset_size": $SMALL_DATASET_SIZE,
      "transformation_type": "complex",
      "duration_seconds": $duration2,
      "rows_per_second": $(echo "scale=2; $SMALL_DATASET_SIZE / $duration2" | bc -l)
    },
    {
      "name": "Medium dataset, simple transformation",
      "dataset_size": $MEDIUM_DATASET_SIZE,
      "transformation_type": "simple",
      "duration_seconds": $duration3,
      "rows_per_second": $(echo "scale=2; $MEDIUM_DATASET_SIZE / $duration3" | bc -l)
    },
    {
      "name": "Medium dataset, complex transformation",
      "dataset_size": $MEDIUM_DATASET_SIZE,
      "transformation_type": "complex",
      "duration_seconds": $duration4,
      "rows_per_second": $(echo "scale=2; $MEDIUM_DATASET_SIZE / $duration4" | bc -l)
    },
    {
      "name": "Large dataset, simple transformation",
      "dataset_size": $LARGE_DATASET_SIZE,
      "transformation_type": "simple",
      "duration_seconds": $duration5,
      "rows_per_second": $(echo "scale=2; $LARGE_DATASET_SIZE / $duration5" | bc -l)
    },
    {
      "name": "Large dataset, intensive transformation",
      "dataset_size": $LARGE_DATASET_SIZE,
      "transformation_type": "intensive",
      "duration_seconds": $duration6,
      "rows_per_second": $(echo "scale=2; $LARGE_DATASET_SIZE / $duration6" | bc -l)
    }
EOF
    
    echo "  ]" >> "$RESULTS_FILE"
    echo "}" >> "$RESULTS_FILE"
    
    # Display summary
    display_results_summary "$duration1" "$duration2" "$duration3" "$duration4" "$duration5" "$duration6"
}

# Function to display results summary
display_results_summary() {
    local d1="$1" d2="$2" d3="$3" d4="$4" d5="$5" d6="$6"
    
    echo ""
    echo -e "${PURPLE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${PURPLE}║                      PERFORMANCE RESULTS                      ║${NC}"
    echo -e "${PURPLE}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    printf "%-40s %-12s %-15s\n" "Test Case" "Duration(s)" "Rows/sec"
    echo "────────────────────────────────────────────────────────────────────"
    printf "%-40s %-12.2f %-15.0f\n" "Small + Simple" "$d1" "$(echo "$SMALL_DATASET_SIZE / $d1" | bc -l)"
    printf "%-40s %-12.2f %-15.0f\n" "Small + Complex" "$d2" "$(echo "$SMALL_DATASET_SIZE / $d2" | bc -l)"
    printf "%-40s %-12.2f %-15.0f\n" "Medium + Simple" "$d3" "$(echo "$MEDIUM_DATASET_SIZE / $d3" | bc -l)"
    printf "%-40s %-12.2f %-15.0f\n" "Medium + Complex" "$d4" "$(echo "$MEDIUM_DATASET_SIZE / $d4" | bc -l)"
    printf "%-40s %-12.2f %-15.0f\n" "Large + Simple" "$d5" "$(echo "$LARGE_DATASET_SIZE / $d5" | bc -l)"
    printf "%-40s %-12.2f %-15.0f\n" "Large + Intensive" "$d6" "$(echo "$LARGE_DATASET_SIZE / $d6" | bc -l)"
    echo ""
    
    # Calculate performance insights
    local best_throughput=$(echo "$LARGE_DATASET_SIZE / $d5" | bc -l)
    local scaling_factor=$(echo "scale=2; $d5 / $d3 / ($LARGE_DATASET_SIZE / $MEDIUM_DATASET_SIZE)" | bc -l)
    
    echo -e "${GREEN}Performance Insights:${NC}"
    echo "• Best throughput: $(printf "%.0f" "$best_throughput") rows/second"
    echo "• Scaling efficiency: $(printf "%.2f" "$scaling_factor") (1.0 = perfect linear scaling)"
    echo "• Results saved to: $RESULTS_FILE"
    echo ""
}

# Function to run single custom test
run_custom_test() {
    local dataset_size="$1"
    local transformation_type="$2"
    local filename="custom_test_${dataset_size}.csv"
    
    log "Running custom test: $dataset_size rows with $transformation_type transformation"
    
    # Generate test data
    generate_test_data "$dataset_size" "$filename"
    
    # Create DSL script
    local dsl_script=$(create_dsl_script "$transformation_type")
    
    # Submit job
    local job_id="custom_test_$(date +%s)"
    local duration=$(submit_job "$filename" "output_custom" "$dsl_script" "$job_id" "Custom Test")
    
    # Display results
    echo ""
    echo -e "${GREEN}Custom Test Results:${NC}"
    echo "Dataset size: $dataset_size rows"
    echo "Transformation: $transformation_type"
    echo "Duration: ${duration}s"
    echo "Throughput: $(echo "scale=0; $dataset_size / $duration" | bc -l) rows/second"
    echo ""
}

# Function to cleanup test data
cleanup_test_data() {
    log "Cleaning up test data..."
    
    rm -f "${DATA_DIR}"/small_test.csv
    rm -f "${DATA_DIR}"/medium_test.csv
    rm -f "${DATA_DIR}"/large_test.csv
    rm -f "${DATA_DIR}"/custom_test_*.csv
    
    log "✅ Test data cleaned up"
}

# Function to show help
show_help() {
    echo "Spark Transform Performance Testing Script"
    echo "=========================================="
    echo ""
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  full-suite           Run complete performance test suite"
    echo "  custom SIZE TYPE     Run custom test with specified size and type"
    echo "  cleanup              Clean up test data files"
    echo "  help                 Show this help"
    echo ""
    echo "Transformation Types:"
    echo "  simple               Basic field concatenation"
    echo "  complex              Multiple transformations with conditionals"
    echo "  intensive            Heavy processing with multiple operations"
    echo ""
    echo "Examples:"
    echo "  $0 full-suite                    # Run all performance tests"
    echo "  $0 custom 10000 complex         # Test 10K rows with complex transformations"
    echo "  $0 cleanup                      # Remove test data files"
    echo ""
}

# Main script logic
main() {
    local command="$1"
    
    case "$command" in
        "full-suite")
            check_prerequisites
            run_test_suite
            ;;
            
        "custom")
            local size="$2"
            local type="$3"
            
            if [[ -z "$size" || -z "$type" ]]; then
                error "Custom test requires size and transformation type"
                show_help
                exit 1
            fi
            
            check_prerequisites
            run_custom_test "$size" "$type"
            ;;
            
        "cleanup")
            cleanup_test_data
            ;;
            
        "help"|"--help"|"-h"|"")
            show_help
            ;;
            
        *)
            error "Unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

# Run main function
main "$@"
