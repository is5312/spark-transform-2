#!/bin/bash

# Spark Transform Cluster - Main Launcher Script
# Simplified entry point for all cluster operations

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

# Function to show banner
show_banner() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════════════╗"
    echo "║               SPARK TRANSFORM CLUSTER MANAGER                    ║"
    echo "║                     Docker-based Deployment                      ║"
    echo "╚═══════════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# Function to show main menu
show_menu() {
    echo -e "${GREEN}Available Commands:${NC}"
    echo ""
    echo -e "${YELLOW}Deployment:${NC}"
    echo "  deploy          Complete deployment (build + deploy + monitor)"
    echo "  quick-deploy    Deploy without building (faster)"
    echo "  build           Build project and Docker images only"
    echo "  start           Start existing cluster"
    echo "  stop            Stop cluster"
    echo "  restart         Restart cluster"
    echo ""
    echo -e "${YELLOW}Monitoring:${NC}"
    echo "  monitor         Real-time cluster monitoring dashboard"
    echo "  status          Show cluster status"
    echo "  health          Run health checks"
    echo "  logs            Show recent logs"
    echo "  urls            Show service URLs"
    echo ""
    echo -e "${YELLOW}Testing:${NC}"
    echo "  test            Run performance test suite"
    echo "  quick-test      Run quick performance test"
    echo ""
    echo -e "${YELLOW}Maintenance:${NC}"
    echo "  backup          Create cluster backup"
    echo "  cleanup         Clean up containers and networks"
    echo "  scale [n]       Scale workers to n instances"
    echo ""
    echo -e "${YELLOW}Information:${NC}"
    echo "  help            Show detailed help"
    echo "  guide           Open comprehensive documentation"
    echo ""
    echo "Examples:"
    echo "  $0 deploy       # Full deployment"
    echo "  $0 monitor      # Start monitoring"
    echo "  $0 test         # Run performance tests"
    echo "  $0 scale 5      # Scale to 5 workers"
    echo ""
}

# Function to handle commands
handle_command() {
    local command="$1"
    shift || true
    
    case "$command" in
        "deploy")
            echo -e "${GREEN}🚀 Starting full deployment...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" full-deploy "$@"
            ;;
            
        "quick-deploy")
            echo -e "${GREEN}⚡ Quick deployment (no build)...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" full-deploy --no-build "$@"
            ;;
            
        "build")
            echo -e "${GREEN}🔨 Building project and images...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" build "$@"
            ;;
            
        "start")
            echo -e "${GREEN}▶️ Starting cluster...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" start "$@"
            ;;
            
        "stop")
            echo -e "${GREEN}⏹️ Stopping cluster...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" stop "$@"
            ;;
            
        "restart")
            echo -e "${GREEN}🔄 Restarting cluster...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" restart "$@"
            ;;
            
        "monitor")
            echo -e "${GREEN}📊 Starting monitoring dashboard...${NC}"
            "${SCRIPT_DIR}/scripts/spark-monitor.sh" "$@"
            ;;
            
        "status")
            echo -e "${GREEN}📋 Checking cluster status...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" status "$@"
            ;;
            
        "health")
            echo -e "${GREEN}💚 Running health checks...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" health "$@"
            ;;
            
        "logs")
            echo -e "${GREEN}📝 Showing logs...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" logs "$@"
            ;;
            
        "urls")
            echo -e "${GREEN}🔗 Service URLs:${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" urls "$@"
            ;;
            
        "test")
            echo -e "${GREEN}🧪 Running performance test suite...${NC}"
            "${SCRIPT_DIR}/scripts/performance-test.sh" full-suite "$@"
            ;;
            
        "quick-test")
            echo -e "${GREEN}⚡ Running quick performance test...${NC}"
            "${SCRIPT_DIR}/scripts/performance-test.sh" custom 5000 simple "$@"
            ;;
            
        "backup")
            echo -e "${GREEN}💾 Creating backup...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" backup "$@"
            ;;
            
        "cleanup")
            echo -e "${GREEN}🧹 Cleaning up...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" cleanup "$@"
            ;;
            
        "scale")
            local workers="$1"
            if [[ -z "$workers" ]]; then
                echo "Usage: $0 scale <number_of_workers>"
                exit 1
            fi
            echo -e "${GREEN}📈 Scaling to $workers workers...${NC}"
            "${SCRIPT_DIR}/scripts/docker-deploy.sh" scale "$workers"
            ;;
            
        "guide")
            echo -e "${GREEN}📖 Opening documentation...${NC}"
            if command -v open &> /dev/null; then
                open "${SCRIPT_DIR}/DOCKER_SCRIPTS_GUIDE.md"
            elif command -v xdg-open &> /dev/null; then
                xdg-open "${SCRIPT_DIR}/DOCKER_SCRIPTS_GUIDE.md"
            else
                echo "Documentation available at: ${SCRIPT_DIR}/DOCKER_SCRIPTS_GUIDE.md"
            fi
            ;;
            
        "help"|"--help"|"-h")
            show_banner
            show_menu
            echo ""
            echo -e "${GREEN}For detailed documentation:${NC}"
            echo "  $0 guide                    # Open full documentation"
            echo "  cat DOCKER_SCRIPTS_GUIDE.md # View documentation in terminal"
            echo ""
            ;;
            
        "")
            show_banner
            show_menu
            ;;
            
        *)
            echo -e "${YELLOW}Unknown command: $command${NC}"
            echo ""
            show_menu
            exit 1
            ;;
    esac
}

# Main execution
main() {
    # If no arguments, show menu
    if [[ $# -eq 0 ]]; then
        show_banner
        show_menu
        exit 0
    fi
    
    # Handle the command
    handle_command "$@"
}

# Run main function with all arguments
main "$@"
