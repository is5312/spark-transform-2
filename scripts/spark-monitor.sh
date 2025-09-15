#!/bin/bash

# Spark Cluster Monitoring Script
# Provides detailed monitoring of the Spark cluster performance

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
REFRESH_INTERVAL=5
LOG_FILE="./logs/monitor.log"

# Create logs directory if it doesn't exist
mkdir -p ./logs

# Function to log with timestamp
log_with_timestamp() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" >> "$LOG_FILE"
}

# Function to get container stats
get_container_stats() {
    docker stats --no-stream --format "{{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}\t{{.PIDs}}" 2>/dev/null || echo "Error getting stats"
}

# Function to get Spark Master info
get_spark_master_info() {
    local master_url="http://localhost:8080/json/"
    curl -s "$master_url" 2>/dev/null | jq -r '
        "Workers: " + (.workers | length | tostring) + 
        " | Cores: " + (.cores | tostring) + 
        " | Memory: " + .memory + 
        " | Apps: " + (.activeapps | length | tostring) + 
        " | Status: " + .status
    ' 2>/dev/null || echo "Master not accessible"
}

# Function to get worker info
get_worker_info() {
    local worker_port="$1"
    local worker_url="http://localhost:${worker_port}/json/"
    curl -s "$worker_url" 2>/dev/null | jq -r '
        "Cores: " + (.cores | tostring) + 
        " | Used: " + (.coresused | tostring) + 
        " | Memory: " + .memory + 
        " | Used: " + .memoryused + 
        " | Executors: " + (.executors | length | tostring)
    ' 2>/dev/null || echo "Worker not accessible"
}

# Function to get application info
get_application_info() {
    curl -s "http://localhost:8080/json/" 2>/dev/null | jq -r '
        .activeapps[] | 
        "ID: " + .id + 
        " | Name: " + .name + 
        " | User: " + .user + 
        " | Cores: " + (.cores | tostring) + 
        " | Duration: " + .duration + 
        " | State: " + .state
    ' 2>/dev/null || echo "No active applications"
}

# Function to get API health
get_api_health() {
    local response=$(curl -s "http://localhost:8084/actuator/health" 2>/dev/null)
    if [[ $? -eq 0 ]]; then
        echo "$response" | jq -r '.status // "Unknown"' 2>/dev/null || echo "Unknown"
    else
        echo "DOWN"
    fi
}

# Function to get recent job submissions
get_recent_jobs() {
    curl -s "http://localhost:8084/actuator/metrics" 2>/dev/null | jq -r '
        .names[] | select(test("transform")) | .
    ' 2>/dev/null | head -5 || echo "No job metrics available"
}

# Function to display dashboard
display_dashboard() {
    clear
    
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║                    SPARK CLUSTER DASHBOARD                    ║${NC}"
    echo -e "${CYAN}╠════════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${CYAN}║ $(date '+%Y-%m-%d %H:%M:%S')                                        ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # Cluster Overview
    echo -e "${YELLOW}📊 CLUSTER OVERVIEW${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    local master_info=$(get_spark_master_info)
    echo -e "${GREEN}Master:${NC} $master_info"
    echo ""
    
    # Worker Status
    echo -e "${YELLOW}👥 WORKER STATUS${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    for port in 8081 8082 8083; do
        local worker_num=$((port - 8080))
        local worker_info=$(get_worker_info $port)
        echo -e "${GREEN}Worker $worker_num:${NC} $worker_info"
    done
    echo ""
    
    # Container Resource Usage
    echo -e "${YELLOW}💾 RESOURCE USAGE${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    printf "%-20s %-10s %-15s %-8s %-15s %-15s %-8s\n" "CONTAINER" "CPU%" "MEMORY" "MEM%" "NET I/O" "BLOCK I/O" "PIDS"
    echo "────────────────────────────────────────────────────────────────────────"
    get_container_stats | while IFS=$'\t' read -r container cpu mem mem_perc net block pids; do
        if [[ -n "$container" ]]; then
            printf "%-20s %-10s %-15s %-8s %-15s %-15s %-8s\n" "$container" "$cpu" "$mem" "$mem_perc" "$net" "$block" "$pids"
        fi
    done
    echo ""
    
    # Active Applications
    echo -e "${YELLOW}🚀 ACTIVE APPLICATIONS${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    local app_info=$(get_application_info)
    if [[ "$app_info" == "No active applications" ]]; then
        echo -e "${PURPLE}No active applications${NC}"
    else
        echo "$app_info"
    fi
    echo ""
    
    # API Status
    echo -e "${YELLOW}🌐 API STATUS${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    local api_health=$(get_api_health)
    if [[ "$api_health" == "UP" ]]; then
        echo -e "${GREEN}Spring Boot API: $api_health${NC}"
    else
        echo -e "${RED}Spring Boot API: $api_health${NC}"
    fi
    echo ""
    
    # System Information
    echo -e "${YELLOW}🖥️  SYSTEM INFORMATION${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "${GREEN}Docker Version:${NC} $(docker --version | cut -d' ' -f3 | tr -d ',')"
    echo -e "${GREEN}Docker Compose:${NC} $(docker-compose --version | cut -d' ' -f3 | tr -d ',')"
    echo -e "${GREEN}Host Memory:${NC} $(free -h | awk '/^Mem:/ {print $2}' 2>/dev/null || echo 'N/A')"
    echo -e "${GREEN}Host CPU:${NC} $(nproc 2>/dev/null || echo 'N/A') cores"
    echo -e "${GREEN}Disk Usage:${NC} $(df -h . | awk 'NR==2 {print $3 "/" $2 " (" $5 ")"}')"
    echo ""
    
    # Log activity (last few lines)
    echo -e "${YELLOW}📝 RECENT LOG ACTIVITY${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    docker-compose logs --tail=3 2>/dev/null | tail -6 || echo "No recent logs"
    echo ""
    
    # Instructions
    echo -e "${CYAN}Press Ctrl+C to exit monitoring | Refresh every ${REFRESH_INTERVAL}s${NC}"
    
    # Log the monitoring data
    log_with_timestamp "Cluster Status - Master: $master_info | API: $api_health"
}

# Function to save snapshot
save_snapshot() {
    local timestamp=$(date '+%Y%m%d_%H%M%S')
    local snapshot_file="./logs/cluster_snapshot_${timestamp}.log"
    
    {
        echo "=================================="
        echo "Spark Cluster Snapshot"
        echo "Timestamp: $(date)"
        echo "=================================="
        echo ""
        echo "MASTER INFO:"
        get_spark_master_info
        echo ""
        echo "WORKER INFO:"
        for port in 8081 8082 8083; do
            echo "Worker $(($port - 8080)):"
            get_worker_info $port
        done
        echo ""
        echo "CONTAINER STATS:"
        get_container_stats
        echo ""
        echo "APPLICATION INFO:"
        get_application_info
        echo ""
        echo "API STATUS:"
        get_api_health
    } > "$snapshot_file"
    
    echo -e "${GREEN}Snapshot saved to: $snapshot_file${NC}"
}

# Function to check alerts
check_alerts() {
    local alerts=()
    
    # Check if any containers are down
    local expected_containers=("spark-master" "spark-worker-1" "spark-worker-2" "spark-worker-3" "spark-history-server" "spring-boot-app")
    for container in "${expected_containers[@]}"; do
        if ! docker ps --format "{{.Names}}" | grep -q "^${container}$"; then
            alerts+=("Container $container is not running")
        fi
    done
    
    # Check API health
    local api_health=$(get_api_health)
    if [[ "$api_health" != "UP" ]]; then
        alerts+=("API is unhealthy: $api_health")
    fi
    
    # Check high CPU usage
    docker stats --no-stream --format "{{.Container}}\t{{.CPUPerc}}" 2>/dev/null | while IFS=$'\t' read -r container cpu; do
        if [[ -n "$container" && "$cpu" =~ ^[0-9]+\.[0-9]+% ]]; then
            local cpu_num=$(echo "$cpu" | sed 's/%//')
            if (( $(echo "$cpu_num > 80" | bc -l) )); then
                alerts+=("High CPU usage on $container: $cpu")
            fi
        fi
    done
    
    # Display alerts if any
    if [[ ${#alerts[@]} -gt 0 ]]; then
        echo -e "${RED}🚨 ALERTS:${NC}"
        for alert in "${alerts[@]}"; do
            echo -e "${RED}  • $alert${NC}"
            log_with_timestamp "ALERT: $alert"
        done
        echo ""
    fi
}

# Function to show usage
show_usage() {
    echo "Spark Cluster Monitoring Script"
    echo "==============================="
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -i, --interval SECONDS    Set refresh interval (default: 5)"
    echo "  -s, --snapshot            Save a snapshot and exit"
    echo "  -a, --alerts              Check for alerts and exit"
    echo "  -h, --help                Show this help"
    echo ""
    echo "Interactive Commands (during monitoring):"
    echo "  s - Save snapshot"
    echo "  a - Check alerts"
    echo "  q - Quit"
    echo ""
}

# Handle command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -i|--interval)
            REFRESH_INTERVAL="$2"
            shift 2
            ;;
        -s|--snapshot)
            save_snapshot
            exit 0
            ;;
        -a|--alerts)
            check_alerts
            exit 0
            ;;
        -h|--help)
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

# Trap to handle cleanup
cleanup() {
    echo ""
    echo -e "${YELLOW}Monitoring stopped.${NC}"
    exit 0
}
trap cleanup SIGINT SIGTERM

# Main monitoring loop
echo -e "${GREEN}Starting Spark Cluster Monitoring...${NC}"
echo -e "${BLUE}Log file: $LOG_FILE${NC}"
echo ""

while true; do
    display_dashboard
    check_alerts
    
    # Non-blocking input check for interactive commands
    read -t $REFRESH_INTERVAL -n 1 input 2>/dev/null || true
    
    case "$input" in
        s|S)
            save_snapshot
            sleep 2
            ;;
        a|A)
            check_alerts
            sleep 2
            ;;
        q|Q)
            cleanup
            ;;
    esac
done
