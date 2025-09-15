#!/bin/bash

# Spark Transform Docker Deployment Script
# Handles build, deploy, and monitoring of the entire Spark cluster

set -e  # Exit on any error

# Configuration
PROJECT_NAME="spark-transform"
DOCKER_IMAGE="custom-spark:latest"
COMPOSE_FILE="docker-compose.yml"
LOG_DIR="./logs"
DATA_DIR="./data"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] WARNING:${NC} $1"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1"
}

info() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')] INFO:${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if Docker is installed and running
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        error "Docker is not running. Please start Docker first."
        exit 1
    fi
    
    # Check if Docker Compose is installed
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
    
    # Check if Gradle is available
    if [[ ! -f "./gradlew" ]]; then
        error "Gradle wrapper not found. Make sure you're in the project root directory."
        exit 1
    fi
    
    log "✅ All prerequisites are met"
}

# Function to clean up previous deployments
cleanup() {
    log "Cleaning up previous deployments..."
    
    # Stop and remove containers
    docker-compose down --remove-orphans 2>/dev/null || true
    
    # Remove unused networks
    docker network prune -f 2>/dev/null || true
    
    # Remove unused volumes (be careful with this)
    if [[ "$1" == "--clean-volumes" ]]; then
        warn "Removing all volumes (this will delete data!)"
        docker volume prune -f 2>/dev/null || true
    fi
    
    log "✅ Cleanup completed"
}

# Function to build the project
build_project() {
    log "Building Gradle project..."
    
    # Clean and build all modules
    ./gradlew clean build --no-daemon --warning-mode all
    
    if [[ $? -eq 0 ]]; then
        log "✅ Gradle build completed successfully"
    else
        error "Gradle build failed"
        exit 1
    fi
}

# Function to build Docker images
build_docker() {
    log "Building Docker images..."
    
    # Build the main Spark image
    docker build -t ${DOCKER_IMAGE} .
    
    if [[ $? -eq 0 ]]; then
        log "✅ Docker image built successfully: ${DOCKER_IMAGE}"
    else
        error "Docker image build failed"
        exit 1
    fi
    
    # Build the Spring Boot app image
    docker build -f spring-boot-app/Dockerfile -t spark-transform-api:latest spring-boot-app/
    
    if [[ $? -eq 0 ]]; then
        log "✅ Spring Boot API image built successfully"
    else
        error "Spring Boot API image build failed"
        exit 1
    fi
}

# Function to prepare directories
prepare_directories() {
    log "Preparing directories..."
    
    # Create data directory if it doesn't exist
    if [[ ! -d "${DATA_DIR}" ]]; then
        mkdir -p "${DATA_DIR}"
        log "Created data directory: ${DATA_DIR}"
    fi
    
    # Create logs directory if it doesn't exist
    if [[ ! -d "${LOG_DIR}" ]]; then
        mkdir -p "${LOG_DIR}"
        log "Created logs directory: ${LOG_DIR}"
    fi
    
    # Set appropriate permissions
    chmod 755 "${DATA_DIR}" "${LOG_DIR}"
    
    log "✅ Directories prepared"
}

# Function to deploy the cluster
deploy() {
    log "Deploying Spark cluster..."
    
    # Start all services
    docker-compose up -d
    
    if [[ $? -eq 0 ]]; then
        log "✅ Spark cluster deployed successfully"
    else
        error "Deployment failed"
        exit 1
    fi
}

# Function to wait for services to be ready
wait_for_services() {
    log "Waiting for services to be ready..."
    
    local max_attempts=60
    local attempt=1
    
    # Wait for Spark Master
    info "Waiting for Spark Master..."
    while [[ $attempt -le $max_attempts ]]; do
        if curl -s http://localhost:8080 > /dev/null 2>&1; then
            log "✅ Spark Master is ready"
            break
        fi
        
        if [[ $attempt -eq $max_attempts ]]; then
            error "Spark Master failed to start after ${max_attempts} attempts"
            return 1
        fi
        
        sleep 5
        ((attempt++))
    done
    
    # Wait for Spring Boot API
    attempt=1
    info "Waiting for Spring Boot API..."
    while [[ $attempt -le $max_attempts ]]; do
        if curl -s http://localhost:8084/actuator/health > /dev/null 2>&1; then
            log "✅ Spring Boot API is ready"
            break
        fi
        
        if [[ $attempt -eq $max_attempts ]]; then
            error "Spring Boot API failed to start after ${max_attempts} attempts"
            return 1
        fi
        
        sleep 5
        ((attempt++))
    done
    
    # Wait for workers to connect
    info "Waiting for Spark Workers to connect..."
    sleep 15  # Give workers time to register
    
    log "✅ All services are ready"
}

# Function to show cluster status
show_status() {
    log "Cluster Status:"
    echo "===================="
    
    # Show container status
    echo -e "${BLUE}Container Status:${NC}"
    docker-compose ps
    echo ""
    
    # Show resource usage
    echo -e "${BLUE}Resource Usage:${NC}"
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"
    echo ""
    
    # Show network information
    echo -e "${BLUE}Network Information:${NC}"
    docker network ls | grep spark
    echo ""
    
    # Show volume information
    echo -e "${BLUE}Volume Information:${NC}"
    docker volume ls | grep spark
    echo ""
}

# Function to show service URLs
show_urls() {
    log "Service URLs:"
    echo "===================="
    echo -e "${GREEN}Spark Master UI:${NC}     http://localhost:8080"
    echo -e "${GREEN}Spark Worker 1 UI:${NC}   http://localhost:8081"
    echo -e "${GREEN}Spark Worker 2 UI:${NC}   http://localhost:8082"
    echo -e "${GREEN}Spark Worker 3 UI:${NC}   http://localhost:8083"
    echo -e "${GREEN}History Server UI:${NC}   http://localhost:18080"
    echo -e "${GREEN}Spring Boot API:${NC}     http://localhost:8084"
    echo -e "${GREEN}API Health Check:${NC}    http://localhost:8084/actuator/health"
    echo ""
}

# Function to run health checks
health_check() {
    log "Running health checks..."
    
    local all_healthy=true
    
    # Check Spark Master
    if curl -s http://localhost:8080 > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Spark Master: Healthy${NC}"
    else
        echo -e "${RED}❌ Spark Master: Unhealthy${NC}"
        all_healthy=false
    fi
    
    # Check Spring Boot API
    if curl -s http://localhost:8084/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Spring Boot API: Healthy${NC}"
    else
        echo -e "${RED}❌ Spring Boot API: Unhealthy${NC}"
        all_healthy=false
    fi
    
    # Check workers (by checking if they're in the containers list)
    for worker in spark-worker-1 spark-worker-2 spark-worker-3; do
        if docker ps --format "{{.Names}}" | grep -q "^${worker}$"; then
            echo -e "${GREEN}✅ ${worker}: Running${NC}"
        else
            echo -e "${RED}❌ ${worker}: Not running${NC}"
            all_healthy=false
        fi
    done
    
    if $all_healthy; then
        log "✅ All services are healthy"
        return 0
    else
        warn "Some services are unhealthy"
        return 1
    fi
}

# Function to show logs
show_logs() {
    local service="$1"
    local lines="${2:-50}"
    
    if [[ -z "$service" ]]; then
        log "Showing logs for all services (last ${lines} lines):"
        docker-compose logs --tail=${lines}
    else
        log "Showing logs for ${service} (last ${lines} lines):"
        docker-compose logs --tail=${lines} "$service"
    fi
}

# Function to monitor in real-time
monitor() {
    log "Starting real-time monitoring (Press Ctrl+C to stop)..."
    
    # Create monitoring script
    cat > /tmp/spark_monitor.sh << 'EOF'
#!/bin/bash
while true; do
    clear
    echo "==================== SPARK CLUSTER MONITORING ===================="
    echo "Timestamp: $(date)"
    echo ""
    
    echo "CONTAINER STATUS:"
    docker-compose ps
    echo ""
    
    echo "RESOURCE USAGE:"
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"
    echo ""
    
    echo "RECENT LOGS (last 5 lines per service):"
    echo "--- Spark Master ---"
    docker-compose logs --tail=5 spark-master 2>/dev/null | tail -5
    echo ""
    echo "--- Spring Boot API ---"
    docker-compose logs --tail=5 spring-boot-app 2>/dev/null | tail -5
    echo ""
    
    sleep 10
done
EOF
    
    chmod +x /tmp/spark_monitor.sh
    bash /tmp/spark_monitor.sh
}

# Function to backup data
backup_data() {
    local backup_name="spark-backup-$(date +%Y%m%d-%H%M%S)"
    local backup_dir="./backups/${backup_name}"
    
    log "Creating backup: ${backup_name}"
    
    mkdir -p "$backup_dir"
    
    # Backup data directory
    if [[ -d "${DATA_DIR}" ]]; then
        cp -r "${DATA_DIR}" "${backup_dir}/"
        log "✅ Data directory backed up"
    fi
    
    # Backup logs directory
    if [[ -d "${LOG_DIR}" ]]; then
        cp -r "${LOG_DIR}" "${backup_dir}/"
        log "✅ Logs directory backed up"
    fi
    
    # Create archive
    tar -czf "${backup_dir}.tar.gz" -C "./backups" "${backup_name}"
    rm -rf "$backup_dir"
    
    log "✅ Backup created: ${backup_dir}.tar.gz"
}

# Function to restore from backup
restore_backup() {
    local backup_file="$1"
    
    if [[ -z "$backup_file" ]]; then
        error "Please specify backup file to restore"
        return 1
    fi
    
    if [[ ! -f "$backup_file" ]]; then
        error "Backup file not found: $backup_file"
        return 1
    fi
    
    log "Restoring from backup: $backup_file"
    
    # Stop services first
    docker-compose down
    
    # Extract backup
    local temp_dir="/tmp/spark-restore-$$"
    mkdir -p "$temp_dir"
    tar -xzf "$backup_file" -C "$temp_dir"
    
    # Restore data
    local backup_name=$(basename "$backup_file" .tar.gz)
    if [[ -d "${temp_dir}/${backup_name}/data" ]]; then
        rm -rf "${DATA_DIR}"
        cp -r "${temp_dir}/${backup_name}/data" "${DATA_DIR}"
        log "✅ Data restored"
    fi
    
    # Cleanup
    rm -rf "$temp_dir"
    
    log "✅ Restore completed"
}

# Function to scale services
scale_services() {
    local workers="$1"
    
    if [[ -z "$workers" ]]; then
        error "Please specify number of workers"
        return 1
    fi
    
    log "Scaling workers to $workers instances..."
    
    # Update docker-compose to scale workers
    docker-compose up -d --scale spark-worker="$workers"
    
    log "✅ Scaled to $workers workers"
}

# Function to run performance test
performance_test() {
    log "Running performance test..."
    
    # Create test data if it doesn't exist
    if [[ ! -f "${DATA_DIR}/test-data.csv" ]]; then
        info "Creating test data..."
        echo "id,name,email,age" > "${DATA_DIR}/test-data.csv"
        for i in {1..10000}; do
            echo "$i,User$i,user$i@example.com,$((20 + RANDOM % 50))" >> "${DATA_DIR}/test-data.csv"
        done
    fi
    
    # Submit test job
    local job_id="perf-test-$(date +%s)"
    
    curl -X POST http://localhost:8084/api/transform/submit \
        -H "Content-Type: application/json" \
        -d "{
            \"inputPath\": \"/opt/data/test-data.csv\",
            \"outputPath\": \"/opt/data/test-output\",
            \"dslScript\": \"{\\\"transformations\\\":[{\\\"target\\\":\\\"full_info\\\",\\\"operation\\\":\\\"concat\\\",\\\"sources\\\":[\\\"name\\\",\\\" - \\\",\\\"email\\\"]}]}\",
            \"jobId\": \"$job_id\"
        }"
    
    log "✅ Performance test submitted with job ID: $job_id"
    info "Monitor the job at: http://localhost:8080"
}

# Function to show help
show_help() {
    echo "Spark Transform Docker Deployment Script"
    echo "========================================="
    echo ""
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  full-deploy       Complete deployment (build + deploy + monitor)"
    echo "  build            Build Gradle project and Docker images"
    echo "  deploy           Deploy the cluster"
    echo "  start            Start existing cluster"
    echo "  stop             Stop the cluster"
    echo "  restart          Restart the cluster"
    echo "  status           Show cluster status"
    echo "  health           Run health checks"
    echo "  monitor          Real-time monitoring"
    echo "  logs [service]   Show logs (optionally for specific service)"
    echo "  cleanup          Clean up containers and networks"
    echo "  backup           Create backup of data and logs"
    echo "  restore [file]   Restore from backup"
    echo "  scale [n]        Scale workers to n instances"
    echo "  test             Run performance test"
    echo "  urls             Show service URLs"
    echo "  help             Show this help"
    echo ""
    echo "Options:"
    echo "  --clean-volumes  Remove volumes during cleanup (DANGER: data loss!)"
    echo "  --no-build       Skip build phase in full-deploy"
    echo ""
    echo "Examples:"
    echo "  $0 full-deploy                    # Complete deployment"
    echo "  $0 logs spring-boot-app           # Show API logs"
    echo "  $0 scale 5                        # Scale to 5 workers"
    echo "  $0 backup                         # Create backup"
    echo "  $0 cleanup --clean-volumes        # Clean including volumes"
    echo ""
}

# Main script logic
main() {
    local command="$1"
    shift || true
    
    case "$command" in
        "full-deploy")
            check_prerequisites
            prepare_directories
            
            if [[ "$1" != "--no-build" ]]; then
                build_project
                build_docker
            fi
            
            cleanup
            deploy
            wait_for_services
            show_status
            show_urls
            health_check
            log "🎉 Full deployment completed successfully!"
            ;;
            
        "build")
            check_prerequisites
            build_project
            build_docker
            ;;
            
        "deploy")
            check_prerequisites
            prepare_directories
            deploy
            wait_for_services
            show_urls
            ;;
            
        "start")
            log "Starting cluster..."
            docker-compose start
            wait_for_services
            show_urls
            ;;
            
        "stop")
            log "Stopping cluster..."
            docker-compose stop
            log "✅ Cluster stopped"
            ;;
            
        "restart")
            log "Restarting cluster..."
            docker-compose restart
            wait_for_services
            show_urls
            ;;
            
        "status")
            show_status
            ;;
            
        "health")
            health_check
            ;;
            
        "monitor")
            monitor
            ;;
            
        "logs")
            show_logs "$1" "$2"
            ;;
            
        "cleanup")
            cleanup "$1"
            ;;
            
        "backup")
            backup_data
            ;;
            
        "restore")
            restore_backup "$1"
            ;;
            
        "scale")
            scale_services "$1"
            ;;
            
        "test")
            performance_test
            ;;
            
        "urls")
            show_urls
            ;;
            
        "help"|"--help"|"-h"|"")
            show_help
            ;;
            
        *)
            error "Unknown command: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
