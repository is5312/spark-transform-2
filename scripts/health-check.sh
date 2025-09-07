#!/bin/bash

# Health check script for Spark Transform Cluster

set -e

echo "🔍 Spark Transform Cluster Health Check"
echo "========================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if a service is running
check_service() {
    local service_name=$1
    local port=$2
    local url=$3
    
    echo -n "Checking $service_name... "
    
    if curl -s --connect-timeout 5 "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Running${NC}"
        return 0
    else
        echo -e "${RED}✗ Not accessible${NC}"
        return 1
    fi
}

# Function to check Docker containers
check_container() {
    local container_name=$1
    
    echo -n "Checking container $container_name... "
    
    if docker ps --format "table {{.Names}}" | grep -q "^${container_name}$"; then
        echo -e "${GREEN}✓ Running${NC}"
        return 0
    else
        echo -e "${RED}✗ Not running${NC}"
        return 1
    fi
}

echo ""
echo "📦 Docker Containers:"
echo "-------------------"

# Check all containers
check_container "spark-master"
check_container "spark-worker-1"
check_container "spark-worker-2"
check_container "spark-worker-3"
check_container "spark-history-server"
check_container "spring-boot-app"

echo ""
echo "🌐 Web Interfaces:"
echo "-----------------"

# Check web interfaces
check_service "Spark Master" "8080" "http://localhost:8080"
check_service "Worker 1" "8081" "http://localhost:8081"
check_service "Worker 2" "8082" "http://localhost:8082"
check_service "Worker 3" "8083" "http://localhost:8083"
check_service "History Server" "18080" "http://localhost:18080"
check_service "Spring Boot API" "8084" "http://localhost:8084/api/transform/health"

echo ""
echo "🔧 API Health Check:"
echo "-------------------"

# Check API health endpoint
echo -n "API Health endpoint... "
health_response=$(curl -s --connect-timeout 5 "http://localhost:8084/api/transform/health" 2>/dev/null)
if [ $? -eq 0 ] && echo "$health_response" | grep -q "UP"; then
    echo -e "${GREEN}✓ Healthy${NC}"
    echo "Response: $health_response"
else
    echo -e "${RED}✗ Unhealthy${NC}"
fi

echo ""
echo "📊 Cluster Status:"
echo "----------------"

# Check Spark cluster status
echo -n "Spark cluster status... "
cluster_info=$(curl -s --connect-timeout 5 "http://localhost:8080" 2>/dev/null)
if [ $? -eq 0 ] && echo "$cluster_info" | grep -q "Workers"; then
    echo -e "${GREEN}✓ Accessible${NC}"
    
    # Extract worker count
    worker_count=$(echo "$cluster_info" | grep -o "Workers: [0-9]" | grep -o "[0-9]" || echo "0")
    echo "  Workers: $worker_count"
    
    # Extract running applications
    app_count=$(echo "$cluster_info" | grep -o "Running Applications: [0-9]" | grep -o "[0-9]" || echo "0")
    echo "  Running Applications: $app_count"
else
    echo -e "${RED}✗ Not accessible${NC}"
fi

echo ""
echo "💾 Data Directory:"
echo "----------------"

# Check data directory
if [ -d "./data" ]; then
    echo -e "${GREEN}✓ Data directory exists${NC}"
    file_count=$(find ./data -name "*.csv" 2>/dev/null | wc -l)
    echo "  CSV files: $file_count"
    
    if [ $file_count -gt 0 ]; then
        total_size=$(du -sh ./data 2>/dev/null | cut -f1)
        echo "  Total size: $total_size"
    fi
else
    echo -e "${YELLOW}⚠ Data directory not found${NC}"
    echo "  Creating data directory..."
    mkdir -p ./data
    echo -e "${GREEN}✓ Data directory created${NC}"
fi

echo ""
echo "📋 Summary:"
echo "----------"

# Count successful checks
total_checks=0
passed_checks=0

# Count container checks
for container in spark-master spark-worker-1 spark-worker-2 spark-worker-3 spark-history-server spring-boot-app; do
    total_checks=$((total_checks + 1))
    if docker ps --format "table {{.Names}}" | grep -q "^${container}$"; then
        passed_checks=$((passed_checks + 1))
    fi
done

# Count service checks
for service in "http://localhost:8080" "http://localhost:8084/api/transform/health"; do
    total_checks=$((total_checks + 1))
    if curl -s --connect-timeout 5 "$service" > /dev/null 2>&1; then
        passed_checks=$((passed_checks + 1))
    fi
done

echo "Passed: $passed_checks/$total_checks checks"

if [ $passed_checks -eq $total_checks ]; then
    echo -e "${GREEN}🎉 All systems operational!${NC}"
    exit 0
elif [ $passed_checks -gt $((total_checks / 2)) ]; then
    echo -e "${YELLOW}⚠ Some issues detected, but cluster is partially functional${NC}"
    exit 1
else
    echo -e "${RED}❌ Major issues detected, cluster needs attention${NC}"
    exit 2
fi
