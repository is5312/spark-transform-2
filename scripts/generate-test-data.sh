#!/bin/bash

# Generate large test data for Spark cluster testing

set -e

DATA_DIR="./test-data"
LARGE_FILE="$DATA_DIR/sample-large.csv"
TARGET_SIZE_MB=${1:-100}  # Default 100MB, can be overridden

echo "Generating large test data file..."
echo "Target size: ${TARGET_SIZE_MB}MB"

# Create data directory if it doesn't exist
mkdir -p "$DATA_DIR"

# Header
echo "id,product_name,category,price,quantity,customer_id,customer_name,customer_email,order_date,region,status,priority" > "$LARGE_FILE"

# Generate data
counter=1
while [ $(du -m "$LARGE_FILE" | cut -f1) -lt $TARGET_SIZE_MB ]; do
    # Generate random data
    product_id=$((counter % 1000 + 1))
    product_name="Product_${product_id}"
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
    
    echo "$counter,$product_name,$category,$price,$quantity,$customer_id,$customer_name,$customer_email,$order_date,$region,$status,$priority" >> "$LARGE_FILE"
    
    counter=$((counter + 1))
    
    # Progress indicator
    if [ $((counter % 10000)) -eq 0 ]; then
        current_size=$(du -m "$LARGE_FILE" | cut -f1)
        echo "Generated $counter records, current size: ${current_size}MB"
    fi
done

final_size=$(du -m "$LARGE_FILE" | cut -f1)
echo "Large test data generation completed!"
echo "File: $LARGE_FILE"
echo "Records: $counter"
echo "Size: ${final_size}MB"
echo "Records per MB: $((counter / final_size))"
