#!/bin/bash

# Cleanup script for Spark Transform Cluster

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "🧹 Spark Transform Cluster Cleanup"
echo "=================================="

# Function to confirm action
confirm() {
    local message=$1
    local default=${2:-n}
    
    if [ "$default" = "y" ]; then
        read -p "$message [Y/n]: " -n 1 -r
    else
        read -p "$message [y/N]: " -n 1 -r
    fi
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        return 0
    else
        return 1
    fi
}

# Function to stop and remove containers
cleanup_containers() {
    echo ""
    echo -e "${BLUE}Docker Containers${NC}"
    echo "----------------"
    
    if docker ps -a --format "table {{.Names}}" | grep -q "spark-"; then
        echo "Found Spark containers:"
        docker ps -a --format "table {{.Names}}\t{{.Status}}" | grep "spark-"
        
        if confirm "Stop and remove all Spark containers?"; then
            echo "Stopping containers..."
            docker-compose down
            
            echo "Removing containers..."
            docker container prune -f
            
            echo -e "${GREEN}✓ Containers cleaned up${NC}"
        else
            echo "Skipping container cleanup"
        fi
    else
        echo -e "${GREEN}✓ No Spark containers found${NC}"
    fi
}

# Function to clean up images
cleanup_images() {
    echo ""
    echo -e "${BLUE}Docker Images${NC}"
    echo "-------------"
    
    if docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | grep -q "custom-spark"; then
        echo "Found custom Spark images:"
        docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | grep "custom-spark"
        
        if confirm "Remove custom Spark images?"; then
            echo "Removing images..."
            docker rmi custom-spark:latest 2>/dev/null || true
            docker image prune -f
            
            echo -e "${GREEN}✓ Images cleaned up${NC}"
        else
            echo "Skipping image cleanup"
        fi
    else
        echo -e "${GREEN}✓ No custom Spark images found${NC}"
    fi
}

# Function to clean up volumes
cleanup_volumes() {
    echo ""
    echo -e "${BLUE}Docker Volumes${NC}"
    echo "--------------"
    
    if docker volume ls --format "table {{.Name}}" | grep -q "spark-transform"; then
        echo "Found Spark volumes:"
        docker volume ls --format "table {{.Name}}\t{{.Driver}}" | grep "spark-transform"
        
        if confirm "Remove Spark volumes?"; then
            echo "Removing volumes..."
            docker volume prune -f
            
            echo -e "${GREEN}✓ Volumes cleaned up${NC}"
        else
            echo "Skipping volume cleanup"
        fi
    else
        echo -e "${GREEN}✓ No Spark volumes found${NC}"
    fi
}

# Function to clean up build artifacts
cleanup_build() {
    echo ""
    echo -e "${BLUE}Build Artifacts${NC}"
    echo "---------------"
    
    if [ -d "build" ] || [ -d "target" ]; then
        echo "Found build directories:"
        find . -name "build" -type d 2>/dev/null | head -5
        find . -name "target" -type d 2>/dev/null | head -5
        
        if confirm "Remove build artifacts?"; then
            echo "Cleaning Gradle build..."
            ./gradlew clean 2>/dev/null || true
            
            echo "Removing build directories..."
            find . -name "build" -type d -exec rm -rf {} + 2>/dev/null || true
            find . -name "target" -type d -exec rm -rf {} + 2>/dev/null || true
            
            echo -e "${GREEN}✓ Build artifacts cleaned up${NC}"
        else
            echo "Skipping build cleanup"
        fi
    else
        echo -e "${GREEN}✓ No build artifacts found${NC}"
    fi
}

# Function to clean up test data
cleanup_test_data() {
    echo ""
    echo -e "${BLUE}Test Data${NC}"
    echo "---------"
    
    if [ -d "data" ] || [ -d "test-data" ]; then
        echo "Found data directories:"
        if [ -d "data" ]; then
            echo "  data/ ($(du -sh data 2>/dev/null | cut -f1))"
        fi
        if [ -d "test-data" ]; then
            echo "  test-data/ ($(du -sh test-data 2>/dev/null | cut -f1))"
        fi
        
        if confirm "Remove test data?"; then
            echo "Removing data directories..."
            rm -rf data test-data 2>/dev/null || true
            
            echo -e "${GREEN}✓ Test data cleaned up${NC}"
        else
            echo "Skipping test data cleanup"
        fi
    else
        echo -e "${GREEN}✓ No test data found${NC}"
    fi
}

# Function to clean up logs
cleanup_logs() {
    echo ""
    echo -e "${BLUE}Log Files${NC}"
    echo "---------"
    
    if find . -name "*.log" -type f 2>/dev/null | grep -q .; then
        echo "Found log files:"
        find . -name "*.log" -type f 2>/dev/null | head -5
        
        if confirm "Remove log files?"; then
            echo "Removing log files..."
            find . -name "*.log" -type f -delete 2>/dev/null || true
            
            echo -e "${GREEN}✓ Log files cleaned up${NC}"
        else
            echo "Skipping log cleanup"
        fi
    else
        echo -e "${GREEN}✓ No log files found${NC}"
    fi
}

# Function to clean up temporary files
cleanup_temp() {
    echo ""
    echo -e "${BLUE}Temporary Files${NC}"
    echo "----------------"
    
    if find . -name "*.tmp" -o -name "*.temp" -o -name "*~" 2>/dev/null | grep -q .; then
        echo "Found temporary files:"
        find . -name "*.tmp" -o -name "*.temp" -o -name "*~" 2>/dev/null | head -5
        
        if confirm "Remove temporary files?"; then
            echo "Removing temporary files..."
            find . -name "*.tmp" -type f -delete 2>/dev/null || true
            find . -name "*.temp" -type f -delete 2>/dev/null || true
            find . -name "*~" -type f -delete 2>/dev/null || true
            
            echo -e "${GREEN}✓ Temporary files cleaned up${NC}"
        else
            echo "Skipping temporary file cleanup"
        fi
    else
        echo -e "${GREEN}✓ No temporary files found${NC}"
    fi
}

# Function to show disk usage
show_disk_usage() {
    echo ""
    echo -e "${BLUE}Disk Usage${NC}"
    echo "----------"
    
    echo "Current directory size:"
    du -sh . 2>/dev/null || echo "Unable to calculate size"
    
    echo ""
    echo "Largest directories:"
    du -sh */ 2>/dev/null | sort -hr | head -5 || echo "No subdirectories found"
}

# Main cleanup process
echo "This script will help you clean up the Spark Transform Cluster setup."
echo ""

# Show current disk usage
show_disk_usage

# Ask what to clean up
echo ""
echo "What would you like to clean up?"
echo "1. Everything (containers, images, volumes, build artifacts, test data)"
echo "2. Docker resources only (containers, images, volumes)"
echo "3. Build artifacts only"
echo "4. Test data only"
echo "5. Custom selection"
echo "6. Show disk usage only"
echo ""

read -p "Choose option (1-6): " choice

case $choice in
    1)
        echo "Cleaning up everything..."
        cleanup_containers
        cleanup_images
        cleanup_volumes
        cleanup_build
        cleanup_test_data
        cleanup_logs
        cleanup_temp
        ;;
    2)
        cleanup_containers
        cleanup_images
        cleanup_volumes
        ;;
    3)
        cleanup_build
        ;;
    4)
        cleanup_test_data
        ;;
    5)
        echo "Custom cleanup selection:"
        cleanup_containers
        cleanup_images
        cleanup_volumes
        cleanup_build
        cleanup_test_data
        cleanup_logs
        cleanup_temp
        ;;
    6)
        show_disk_usage
        exit 0
        ;;
    *)
        echo "Invalid choice. Exiting."
        exit 1
        ;;
esac

# Show final disk usage
echo ""
echo "Final disk usage:"
show_disk_usage

echo ""
echo -e "${GREEN}🎉 Cleanup completed!${NC}"
