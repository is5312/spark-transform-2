#!/bin/bash

# Master test runner for Spark Transform Cluster

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "🧪 Spark Transform Cluster - Complete Test Suite"
echo "================================================"

# Test results tracking
declare -A test_results
total_tests=0
passed_tests=0

# Function to run a test and track results
run_test() {
    local test_name=$1
    local test_script=$2
    local description=$3
    
    echo ""
    echo -e "${BLUE}=== $test_name ===${NC}"
    echo "$description"
    echo "Running: $test_script"
    echo ""
    
    total_tests=$((total_tests + 1))
    
    if [ -f "$test_script" ] && [ -x "$test_script" ]; then
        if $test_script; then
            echo -e "${GREEN}✓ $test_name PASSED${NC}"
            test_results[$test_name]="PASSED"
            passed_tests=$((passed_tests + 1))
        else
            echo -e "${RED}✗ $test_name FAILED${NC}"
            test_results[$test_name]="FAILED"
        fi
    else
        echo -e "${RED}✗ $test_name SKIPPED (script not found or not executable)${NC}"
        test_results[$test_name]="SKIPPED"
    fi
}

# Function to show test summary
show_summary() {
    echo ""
    echo "📊 Test Results Summary"
    echo "======================"
    
    for test_name in "${!test_results[@]}"; do
        local status=${test_results[$test_name]}
        case $status in
            "PASSED")
                echo -e "  ${GREEN}✓${NC} $test_name: PASSED"
                ;;
            "FAILED")
                echo -e "  ${RED}✗${NC} $test_name: FAILED"
                ;;
            "SKIPPED")
                echo -e "  ${YELLOW}⚠${NC} $test_name: SKIPPED"
                ;;
        esac
    done
    
    echo ""
    echo "Overall Results:"
    echo "  Total tests: $total_tests"
    echo "  Passed: $passed_tests"
    echo "  Failed: $((total_tests - passed_tests))"
    echo "  Success rate: $(( (passed_tests * 100) / total_tests ))%"
    
    if [ $passed_tests -eq $total_tests ]; then
        echo -e "${GREEN}🎉 All tests passed!${NC}"
        return 0
    elif [ $passed_tests -gt $((total_tests / 2)) ]; then
        echo -e "${YELLOW}⚠ Some tests failed, but most passed${NC}"
        return 1
    else
        echo -e "${RED}❌ Most tests failed${NC}"
        return 2
    fi
}

# Function to check prerequisites
check_prerequisites() {
    echo "🔍 Checking Prerequisites"
    echo "========================"
    
    local missing_tools=()
    
    for tool in docker docker-compose curl jq; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            missing_tools+=("$tool")
        fi
    done
    
    if [ ${#missing_tools[@]} -gt 0 ]; then
        echo -e "${RED}✗ Missing required tools: ${missing_tools[*]}${NC}"
        echo "Please install the missing tools and try again."
        exit 1
    else
        echo -e "${GREEN}✓ All required tools are available${NC}"
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --quick          Run quick tests only (health check + basic API)"
    echo "  --full           Run full test suite (default)"
    echo "  --performance    Run performance tests only"
    echo "  --validation     Run validation tests only"
    echo "  --help           Show this help message"
    echo ""
    echo "Test Categories:"
    echo "  - Health Check: Verify cluster is running and accessible"
    echo "  - API Tests: Test REST API endpoints and transformations"
    echo "  - End-to-End: Complete workflow from build to results"
    echo "  - Performance: Test with various data sizes"
    echo "  - Validation: Verify output data quality"
    echo ""
}

# Parse command line arguments
QUICK_MODE=false
PERFORMANCE_ONLY=false
VALIDATION_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --quick)
            QUICK_MODE=true
            shift
            ;;
        --full)
            QUICK_MODE=false
            shift
            ;;
        --performance)
            PERFORMANCE_ONLY=true
            shift
            ;;
        --validation)
            VALIDATION_ONLY=true
            shift
            ;;
        --help)
            show_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Main execution
echo "Starting test suite..."

# Check prerequisites
check_prerequisites

# Quick mode tests
if [ "$QUICK_MODE" = true ]; then
    echo ""
    echo "🚀 Running Quick Tests"
    echo "====================="
    
    run_test "Health Check" "./scripts/health-check.sh" "Verify cluster health and accessibility"
    run_test "Basic API Test" "./scripts/test-api.sh" "Test basic API functionality"
    
    show_summary
    exit $?
fi

# Performance only tests
if [ "$PERFORMANCE_ONLY" = true ]; then
    echo ""
    echo "⚡ Running Performance Tests"
    echo "==========================="
    
    run_test "Performance Test" "./scripts/test-performance.sh" "Test cluster performance with various data sizes"
    
    show_summary
    exit $?
fi

# Validation only tests
if [ "$VALIDATION_ONLY" = true ]; then
    echo ""
    echo "🔍 Running Validation Tests"
    echo "=========================="
    
    run_test "Output Validation" "./scripts/validate-output.sh" "Validate transformation output quality"
    
    show_summary
    exit $?
fi

# Full test suite
echo ""
echo "🎯 Running Full Test Suite"
echo "========================="

# Build and setup tests
run_test "Build Test" "./scripts/build.sh" "Build project and Docker images"

# Health and basic functionality tests
run_test "Health Check" "./scripts/health-check.sh" "Verify cluster health and accessibility"

# API and transformation tests
run_test "API Tests" "./scripts/test-api.sh" "Test REST API endpoints and transformations"

# End-to-end workflow test
run_test "End-to-End Test" "./scripts/test-end-to-end.sh" "Complete workflow from build to results"

# Performance tests
run_test "Performance Test" "./scripts/test-performance.sh" "Test cluster performance with various data sizes"

# Validation tests
run_test "Output Validation" "./scripts/validate-output.sh" "Validate transformation output quality"

# Show final summary
show_summary
