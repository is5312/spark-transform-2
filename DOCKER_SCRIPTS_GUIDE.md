# Docker Deployment and Monitoring Scripts Guide

This guide covers the comprehensive set of scripts for building, deploying, and monitoring your Spark Transform cluster using Docker.

## 📁 Scripts Overview

### 1. **docker-deploy.sh** - Main Deployment Script
Complete Docker workflow management from build to deploy and basic monitoring.

### 2. **spark-monitor.sh** - Real-time Cluster Monitoring  
Advanced monitoring dashboard with real-time metrics and alerts.

### 3. **performance-test.sh** - Performance Testing Suite
Comprehensive performance testing with various dataset sizes and transformation complexities.

---

## 🚀 Quick Start

### Complete Deployment (Recommended)
```bash
# Full deployment with build, deploy, and monitoring
./scripts/docker-deploy.sh full-deploy

# Or without rebuilding (if already built)
./scripts/docker-deploy.sh full-deploy --no-build
```

### Individual Commands
```bash
# Build only
./scripts/docker-deploy.sh build

# Deploy only (after build)
./scripts/docker-deploy.sh deploy

# Monitor cluster
./scripts/spark-monitor.sh
```

---

## 📋 Script Detailed Usage

## 1. docker-deploy.sh

### Available Commands

#### **full-deploy**
Complete end-to-end deployment process.
```bash
./scripts/docker-deploy.sh full-deploy
```
**What it does:**
- ✅ Checks prerequisites (Docker, Gradle)
- 🧹 Cleans up previous deployments
- 🔨 Builds Gradle project
- 🐳 Builds Docker images
- 📁 Prepares directories
- 🚀 Deploys cluster
- ⏳ Waits for services to be ready
- 📊 Shows status and URLs
- 💚 Runs health checks

#### **build**
Build project and Docker images.
```bash
./scripts/docker-deploy.sh build
```

#### **deploy**
Deploy the cluster (assumes images are built).
```bash
./scripts/docker-deploy.sh deploy
```

#### **start/stop/restart**
Control existing cluster.
```bash
./scripts/docker-deploy.sh start
./scripts/docker-deploy.sh stop
./scripts/docker-deploy.sh restart
```

#### **status**
Show detailed cluster status.
```bash
./scripts/docker-deploy.sh status
```

#### **health**
Run comprehensive health checks.
```bash
./scripts/docker-deploy.sh health
```

#### **monitor**
Start basic real-time monitoring.
```bash
./scripts/docker-deploy.sh monitor
```

#### **logs**
View service logs.
```bash
./scripts/docker-deploy.sh logs                    # All services
./scripts/docker-deploy.sh logs spring-boot-app    # Specific service
./scripts/docker-deploy.sh logs spark-master       # Master logs
```

#### **cleanup**
Clean up containers and networks.
```bash
./scripts/docker-deploy.sh cleanup
./scripts/docker-deploy.sh cleanup --clean-volumes  # ⚠️ Removes data!
```

#### **backup/restore**
Backup and restore cluster data.
```bash
./scripts/docker-deploy.sh backup
./scripts/docker-deploy.sh restore ./backups/spark-backup-20231215-143022.tar.gz
```

#### **scale**
Scale worker instances.
```bash
./scripts/docker-deploy.sh scale 5  # Scale to 5 workers
```

#### **test**
Run quick performance test.
```bash
./scripts/docker-deploy.sh test
```

#### **urls**
Show all service URLs.
```bash
./scripts/docker-deploy.sh urls
```

### Service URLs
After successful deployment:
- **Spark Master UI:** http://localhost:8080
- **Spark Workers:** http://localhost:8081, 8082, 8083
- **History Server:** http://localhost:18080
- **Spring Boot API:** http://localhost:8084
- **API Health:** http://localhost:8084/actuator/health

---

## 2. spark-monitor.sh

Advanced real-time monitoring with beautiful dashboard interface.

### Basic Usage
```bash
./scripts/spark-monitor.sh                    # Start monitoring
./scripts/spark-monitor.sh -i 10              # 10-second refresh interval
./scripts/spark-monitor.sh --snapshot          # Save snapshot and exit
./scripts/spark-monitor.sh --alerts           # Check alerts and exit
```

### Interactive Commands (during monitoring)
- **s** - Save snapshot
- **a** - Check alerts  
- **q** - Quit monitoring

### Features
- 📊 **Real-time Dashboard** - Live cluster metrics
- 🔍 **Resource Monitoring** - CPU, Memory, Network, Disk usage
- 👥 **Worker Status** - Individual worker performance
- 🚀 **Application Tracking** - Active Spark applications
- 🌐 **API Health** - Spring Boot API status
- 🚨 **Alert System** - Automated problem detection
- 📸 **Snapshots** - Save monitoring state
- 📝 **Logging** - Persistent monitoring logs

### Alert Conditions
- Container down/unhealthy
- High CPU usage (>80%)
- API unhealthy
- Memory exhaustion
- Network issues

---

## 3. performance-test.sh

Comprehensive performance testing suite with multiple test scenarios.

### Full Test Suite
```bash
./scripts/performance-test.sh full-suite
```

**Test Scenarios:**
1. **Small + Simple** (1K rows, basic transformation)
2. **Small + Complex** (1K rows, multi-step transformation)
3. **Medium + Simple** (50K rows, basic transformation) 
4. **Medium + Complex** (50K rows, multi-step transformation)
5. **Large + Simple** (500K rows, basic transformation)
6. **Large + Intensive** (500K rows, heavy processing)

### Custom Tests
```bash
./scripts/performance-test.sh custom 100000 complex
./scripts/performance-test.sh custom 25000 simple
./scripts/performance-test.sh custom 1000000 intensive
```

### Transformation Types
- **simple** - Basic field concatenation
- **complex** - Multiple transformations with conditionals
- **intensive** - Heavy processing with 6+ operations

### Test Data Cleanup
```bash
./scripts/performance-test.sh cleanup
```

### Results
- Results saved to `./test-results/performance_test_TIMESTAMP.json`
- Summary displayed with throughput metrics
- Performance insights and scaling analysis

---

## 🔧 Configuration

### Environment Variables
```bash
# Database Configuration
export POSTGRES_HOST=your-db-host
export POSTGRES_DB=your-database
export POSTGRES_USER=your-username
export POSTGRES_PASSWORD=your-password

# Redis Configuration  
export REDIS_HOST=your-redis-host
export REDIS_PORT=6379

# Spring Profiles
export SPRING_PROFILES_ACTIVE=prod,docker
```

### Docker Compose Profiles
Set different Spring profiles for different environments:
```bash
# Development
SPRING_PROFILES_ACTIVE=dev,docker

# Production
SPRING_PROFILES_ACTIVE=prod,docker

# Testing
SPRING_PROFILES_ACTIVE=test,docker
```

---

## 📊 Monitoring and Alerts

### Real-time Monitoring Features
```bash
# Start comprehensive monitoring
./scripts/spark-monitor.sh

# Monitor with custom refresh interval
./scripts/spark-monitor.sh --interval 15

# Quick health check
./scripts/docker-deploy.sh health
```

### Log Management
```bash
# View recent logs
./scripts/docker-deploy.sh logs

# View specific service logs
./scripts/docker-deploy.sh logs spark-master
./scripts/docker-deploy.sh logs spring-boot-app

# Follow logs in real-time
docker-compose logs -f
```

### Performance Monitoring
```bash
# Run performance tests
./scripts/performance-test.sh full-suite

# Monitor during tests
./scripts/spark-monitor.sh &
./scripts/performance-test.sh custom 100000 complex
```

---

## 🔄 Typical Workflows

### Development Workflow
```bash
# 1. Full deployment
./scripts/docker-deploy.sh full-deploy

# 2. Start monitoring
./scripts/spark-monitor.sh &

# 3. Run tests
./scripts/performance-test.sh custom 10000 simple

# 4. Check logs if needed
./scripts/docker-deploy.sh logs spring-boot-app

# 5. Cleanup when done
./scripts/docker-deploy.sh cleanup
```

### Production Deployment
```bash
# 1. Set production environment
export SPRING_PROFILES_ACTIVE=prod,docker
export POSTGRES_HOST=prod-db.company.com
export REDIS_HOST=prod-redis.company.com

# 2. Deploy with production settings
./scripts/docker-deploy.sh full-deploy

# 3. Run health checks
./scripts/docker-deploy.sh health

# 4. Start monitoring
./scripts/spark-monitor.sh

# 5. Run performance validation
./scripts/performance-test.sh full-suite
```

### Troubleshooting Workflow
```bash
# 1. Check overall status
./scripts/docker-deploy.sh status

# 2. Check health
./scripts/docker-deploy.sh health

# 3. Check logs
./scripts/docker-deploy.sh logs

# 4. Monitor real-time
./scripts/spark-monitor.sh

# 5. Check alerts
./scripts/spark-monitor.sh --alerts

# 6. Restart if needed
./scripts/docker-deploy.sh restart
```

---

## 📁 Directory Structure

```
spark-transform-2/
├── scripts/
│   ├── docker-deploy.sh         # Main deployment script
│   ├── spark-monitor.sh         # Monitoring dashboard
│   ├── performance-test.sh      # Performance testing
│   └── ...other scripts...
├── data/                        # Input/output data
├── logs/                        # Application logs
├── test-results/               # Performance test results
├── backups/                    # Backup files
└── docker-compose.yml          # Docker configuration
```

---

## 🔍 Troubleshooting

### Common Issues

#### **Services won't start**
```bash
# Check Docker status
docker info

# Check logs
./scripts/docker-deploy.sh logs

# Try cleanup and redeploy
./scripts/docker-deploy.sh cleanup
./scripts/docker-deploy.sh full-deploy
```

#### **Performance issues**
```bash
# Check resource usage
./scripts/spark-monitor.sh

# Scale workers
./scripts/docker-deploy.sh scale 5

# Check for alerts
./scripts/spark-monitor.sh --alerts
```

#### **API not responding**
```bash
# Check API health
curl http://localhost:8084/actuator/health

# Check API logs
./scripts/docker-deploy.sh logs spring-boot-app

# Restart API
docker-compose restart spring-boot-app
```

#### **Spark jobs failing**
```bash
# Check Spark Master UI
open http://localhost:8080

# Check worker logs
./scripts/docker-deploy.sh logs spark-worker-1

# Check application logs
./scripts/docker-deploy.sh logs
```

### Log Locations
- **Monitor logs:** `./logs/monitor.log`
- **Performance results:** `./test-results/`
- **Docker logs:** `docker-compose logs`
- **Application logs:** Container-specific logs

### Data Recovery
```bash
# List available backups
ls -la ./backups/

# Restore from backup
./scripts/docker-deploy.sh restore ./backups/spark-backup-TIMESTAMP.tar.gz
```

---

## ⚡ Performance Tips

### Optimization Strategies
1. **Scale workers** based on workload
2. **Monitor resource usage** during peak times
3. **Use appropriate profiles** (dev/prod)
4. **Regular backups** before major changes
5. **Performance testing** after configuration changes

### Resource Planning
- **Small datasets** (< 10K rows): 1-2 workers
- **Medium datasets** (10K-100K rows): 3-4 workers  
- **Large datasets** (> 100K rows): 5+ workers

### Monitoring Best Practices
- Run monitoring during performance tests
- Set up alerts for production environments
- Regular snapshot saves for trend analysis
- Monitor during high-load periods

This comprehensive script suite provides everything needed for professional Docker-based Spark cluster management!
