#!/bin/bash

# Output validation script for Spark Transform Cluster

set -e

OUTPUT_DIR="./data/output"
TEST_DATA_DIR="./test-data"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "🔍 Spark Transform Output Validation"
echo "===================================="

# Function to validate CSV file
validate_csv() {
    local file_path=$1
    local expected_columns=$2
    
    echo "Validating: $(basename "$file_path")"
    
    if [ ! -f "$file_path" ]; then
        echo -e "  ${RED}✗ File not found${NC}"
        return 1
    fi
    
    # Check if file has content
    local line_count=$(wc -l < "$file_path")
    if [ $line_count -lt 2 ]; then
        echo -e "  ${RED}✗ File is empty or has no data rows${NC}"
        return 1
    fi
    
    # Check header
    local header=$(head -1 "$file_path")
    echo "  Header: $header"
    
    # Check if expected columns are present
    if [ -n "$expected_columns" ]; then
        for column in $expected_columns; do
            if echo "$header" | grep -q "$column"; then
                echo -e "  ${GREEN}✓${NC} Column '$column' found"
            else
                echo -e "  ${YELLOW}⚠${NC} Column '$column' not found"
            fi
        done
    fi
    
    # Check data rows
    local data_rows=$((line_count - 1))
    echo "  Data rows: $data_rows"
    
    # Check for empty rows
    local empty_rows=$(grep -c "^$" "$file_path" 2>/dev/null || echo "0")
    if [ $empty_rows -gt 0 ]; then
        echo -e "  ${YELLOW}⚠${NC} Found $empty_rows empty rows"
    fi
    
    # Check for malformed rows (different column count)
    local header_columns=$(echo "$header" | tr ',' '\n' | wc -l)
    local malformed_rows=0
    
    while IFS= read -r line; do
        local line_columns=$(echo "$line" | tr ',' '\n' | wc -l)
        if [ $line_columns -ne $header_columns ]; then
            malformed_rows=$((malformed_rows + 1))
        fi
    done < <(tail -n +2 "$file_path")
    
    if [ $malformed_rows -gt 0 ]; then
        echo -e "  ${RED}✗${NC} Found $malformed_rows malformed rows"
        return 1
    else
        echo -e "  ${GREEN}✓${NC} All rows have correct column count"
    fi
    
    return 0
}

# Function to compare input and output
compare_files() {
    local input_file=$1
    local output_file=$2
    
    echo "Comparing input and output files..."
    
    if [ ! -f "$input_file" ] || [ ! -f "$output_file" ]; then
        echo -e "  ${RED}✗${NC} Input or output file not found"
        return 1
    fi
    
    local input_rows=$(wc -l < "$input_file")
    local output_rows=$(wc -l < "$output_file")
    
    echo "  Input rows: $input_rows"
    echo "  Output rows: $output_rows"
    
    if [ $input_rows -eq $output_rows ]; then
        echo -e "  ${GREEN}✓${NC} Row count matches"
    else
        echo -e "  ${YELLOW}⚠${NC} Row count differs (input: $input_rows, output: $output_rows)"
    fi
    
    # Check if output has more columns (transformations should add columns)
    local input_columns=$(head -1 "$input_file" | tr ',' '\n' | wc -l)
    local output_columns=$(head -1 "$output_file" | tr ',' '\n' | wc -l)
    
    echo "  Input columns: $input_columns"
    echo "  Output columns: $output_columns"
    
    if [ $output_columns -gt $input_columns ]; then
        echo -e "  ${GREEN}✓${NC} Output has more columns (transformations applied)"
    elif [ $output_columns -eq $input_columns ]; then
        echo -e "  ${YELLOW}⚠${NC} Same number of columns (no new columns added)"
    else
        echo -e "  ${RED}✗${NC} Output has fewer columns than input"
    fi
}

# Function to check transformation results
check_transformations() {
    local output_file=$1
    
    echo "Checking transformation results..."
    
    # Sample a few rows to check transformations
    local sample_rows=$(head -5 "$output_file" | tail -4)
    
    while IFS= read -r row; do
        if [ -n "$row" ]; then
            echo "  Sample row: $row"
            
            # Check for common transformations
            if echo "$row" | grep -q "FULL_NAME\|full_name"; then
                echo -e "    ${GREEN}✓${NC} Name concatenation detected"
            fi
            
            if echo "$row" | grep -q "@.*\.COM\|@.*\.com"; then
                echo -e "    ${GREEN}✓${NC} Email processing detected"
            fi
            
            if echo "$row" | grep -q "[0-9]\+\.[0-9]\+"; then
                echo -e "    ${GREEN}✓${NC} Numeric calculations detected"
            fi
        fi
    done <<< "$sample_rows"
}

# Main validation
echo ""
echo "📁 Checking output directory..."

if [ ! -d "$OUTPUT_DIR" ]; then
    echo -e "${RED}✗${NC} Output directory not found: $OUTPUT_DIR"
    exit 1
fi

echo -e "${GREEN}✓${NC} Output directory found"

# Find all CSV files in output directory
output_files=$(find "$OUTPUT_DIR" -name "*.csv" -type f)

if [ -z "$output_files" ]; then
    echo -e "${YELLOW}⚠${NC} No CSV files found in output directory"
    exit 0
fi

echo "Found $(echo "$output_files" | wc -l) output files"

# Validate each output file
echo ""
echo "🔍 Validating output files..."

for output_file in $output_files; do
    echo ""
    echo -e "${BLUE}=== $(basename "$output_file") ===${NC}"
    
    # Basic CSV validation
    if validate_csv "$output_file"; then
        echo -e "  ${GREEN}✓${NC} Basic validation passed"
    else
        echo -e "  ${RED}✗${NC} Basic validation failed"
        continue
    fi
    
    # Check transformations
    check_transformations "$output_file"
    
    # Try to find corresponding input file
    local test_name=$(basename "$(dirname "$output_file")")
    local input_file=""
    
    # Look for input files in test-data directory
    for input_candidate in "$TEST_DATA_DIR"/*.csv; do
        if [ -f "$input_candidate" ]; then
            input_file="$input_candidate"
            break
        fi
    done
    
    # Also check data directory
    if [ -z "$input_file" ]; then
        for input_candidate in "./data"/*.csv; do
            if [ -f "$input_candidate" ] && [[ "$input_candidate" != *"output"* ]]; then
                input_file="$input_candidate"
                break
            fi
        done
    fi
    
    if [ -n "$input_file" ]; then
        echo ""
        echo "Comparing with input file: $(basename "$input_file")"
        compare_files "$input_file" "$output_file"
    else
        echo -e "  ${YELLOW}⚠${NC} No input file found for comparison"
    fi
done

# Summary
echo ""
echo "📊 Validation Summary"
echo "===================="

total_files=$(echo "$output_files" | wc -l)
valid_files=0

for output_file in $output_files; do
    if validate_csv "$output_file" > /dev/null 2>&1; then
        valid_files=$((valid_files + 1))
    fi
done

echo "Total output files: $total_files"
echo "Valid files: $valid_files"

if [ $valid_files -eq $total_files ] && [ $total_files -gt 0 ]; then
    echo -e "${GREEN}🎉 All output files are valid!${NC}"
    exit 0
elif [ $valid_files -gt 0 ]; then
    echo -e "${YELLOW}⚠ Some output files have issues${NC}"
    exit 1
else
    echo -e "${RED}❌ No valid output files found${NC}"
    exit 2
fi
