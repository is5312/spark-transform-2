# Spark Transform Cluster - Testing Guide

This document provides comprehensive testing instructions for the Spark Transform Cluster setup.

## 🧪 Test Suite Overview

The test suite includes multiple types of tests to ensure the cluster works correctly:

- **Health Checks**: Verify cluster components are running
- **API Tests**: Test REST endpoints and transformations
- **End-to-End Tests**: Complete workflow validation
- **Performance Tests**: Test with various data sizes
- **Validation Tests**: Verify output data quality

## 📋 Prerequisites

Before running tests, ensure you have:

- Docker and Docker Compose installed
- Java 17+ (optional, Docker will handle this)
- `curl` and `jq` command-line tools
- At least 4GB of available RAM
- 10GB+ of free disk space

## 🚀 Quick Start Testing

### Run All Tests
```bash
# Run complete test suite
./scripts/run-all-tests.sh

# Run quick tests only
./scripts/run-all-tests.sh --quick

# Run performance tests only
./scripts/run-all-tests.sh --performance

# Run validation tests only
./scripts/run-all-tests.sh --validation
```

### Individual Test Scripts

#### 1. Health Check
```bash
./scripts/health-check.sh
```
**What it tests:**
- Docker containers are running
- Web interfaces are accessible
- API health endpoint responds
- Spark cluster is operational

#### 2. API Tests
```bash
./scripts/test-api.sh
```
**What it tests:**
- REST API endpoints
- Job submission and monitoring
- DSL transformation execution
- Error handling

#### 3. End-to-End Test
```bash
./scripts/test-end-to-end.sh
```
**What it tests:**
- Complete build process
- Cluster startup
- Data processing workflow
- Output generation

#### 4. Performance Test
```bash
./scripts/test-performance.sh
```
**What it tests:**
- Processing speed with different file sizes
- Memory usage and optimization
- Cluster scalability
- Throughput metrics

#### 5. Output Validation
```bash
./scripts/validate-output.sh
```
**What it tests:**
- Output file integrity
- Data transformation accuracy
- Column structure validation
- Row count verification

## 📊 Test Data

### Sample Data Files

The test suite includes several sample data files:

- **`test-data/sample-small.csv`**: 10 records, basic employee data
- **`test-data/sample-medium.csv`**: 20 records, e-commerce data
- **Generated large files**: Created dynamically for performance testing

### Generate Custom Test Data

```bash
# Generate 100MB test file
./scripts/generate-test-data.sh 100

# Generate 500MB test file (for large-scale testing)
./scripts/generate-test-data.sh 500
```

## 🔧 Test Configuration

### Environment Variables

You can customize test behavior with environment variables:

```bash
# Set API base URL (default: http://localhost:8084)
export API_BASE="http://localhost:8084/api/transform"

# Set test data directory (default: ./test-data)
export TEST_DATA_DIR="./test-data"

# Set output directory (default: ./data/output)
export OUTPUT_DIR="./data/output"
```

### Test Timeouts

Default timeouts can be adjusted in the scripts:
- Health check timeout: 60 seconds
- Job completion timeout: 300 seconds (5 minutes)
- Performance test timeout: 600 seconds (10 minutes)

## 📈 Performance Benchmarks

### Expected Performance

Based on the cluster configuration (1 master, 3 workers):

| File Size | Expected Duration | Records/Second | MB/Second |
|-----------|------------------|----------------|-----------|
| 10MB      | 10-30 seconds    | 1,000-5,000   | 0.3-1.0   |
| 50MB      | 30-90 seconds    | 2,000-8,000   | 0.6-1.7   |
| 100MB     | 60-180 seconds   | 3,000-10,000  | 0.6-1.7   |
| 500MB     | 300-900 seconds  | 5,000-15,000  | 0.6-1.7   |

*Note: Performance depends on hardware, network, and data complexity.*

## 🐛 Troubleshooting

### Common Issues

#### 1. Cluster Not Starting
```bash
# Check Docker status
docker ps

# Check logs
docker-compose logs

# Restart cluster
docker-compose down && docker-compose up -d
```

#### 2. API Not Responding
```bash
# Check Spring Boot container
docker logs spring-boot-app

# Check port availability
netstat -an | grep 8084
```

#### 3. Jobs Failing
```bash
# Check Spark master logs
docker logs spark-master

# Check worker logs
docker logs spark-worker-1

# Check job history
curl http://localhost:18080
```

#### 4. Out of Memory Errors
```bash
# Increase worker memory in docker-compose.yml
SPARK_WORKER_MEMORY=8g

# Restart cluster
docker-compose down && docker-compose up -d
```

### Debug Mode

Enable debug logging for detailed troubleshooting:

```bash
# Set debug environment variable
export DEBUG=true

# Run tests with verbose output
./scripts/test-api.sh 2>&1 | tee debug.log
```

## 📝 Test Results

### Output Files

Test results are saved in:
- **`./data/output/`**: Transformation output files
- **`./data/performance-results.csv`**: Performance metrics
- **`./data/performance-report.txt`**: Detailed performance report

### Log Files

Logs are available in:
- **Docker logs**: `docker-compose logs`
- **Application logs**: Container stdout/stderr
- **Spark logs**: Available in Spark UI

## 🧹 Cleanup

### Clean Up Test Data
```bash
# Remove all test data and outputs
./scripts/cleanup.sh

# Remove only Docker resources
./scripts/cleanup.sh --docker-only

# Remove only build artifacts
./scripts/cleanup.sh --build-only
```

### Manual Cleanup
```bash
# Stop and remove containers
docker-compose down

# Remove images
docker rmi custom-spark:latest

# Remove volumes
docker volume prune

# Remove build artifacts
./gradlew clean
rm -rf build/ target/
```

## 🔄 Continuous Testing

### Automated Testing

For CI/CD integration, use the non-interactive mode:

```bash
# Run tests without user interaction
./scripts/run-all-tests.sh --quick --non-interactive

# Exit with proper codes for CI
if [ $? -eq 0 ]; then
    echo "All tests passed"
    exit 0
else
    echo "Some tests failed"
    exit 1
fi
```

### Monitoring

Set up monitoring for production use:

```bash
# Health check every 5 minutes
*/5 * * * * /path/to/scripts/health-check.sh

# Performance monitoring
0 */6 * * * /path/to/scripts/test-performance.sh
```

## 📚 Additional Resources

### Web Interfaces

- **Spark Master**: http://localhost:8080
- **Spark Workers**: http://localhost:8081, 8082, 8083
- **History Server**: http://localhost:18080
- **Spring Boot API**: http://localhost:8084

### Documentation

- [Main README](README.md)
- [API Documentation](examples/)
- [Docker Configuration](docker-compose.yml)

### Support

For issues or questions:
1. Check the troubleshooting section above
2. Review Docker and application logs
3. Verify cluster health with health check script
4. Check system resources (CPU, memory, disk)

## 🎯 Test Checklist

Before considering the setup production-ready:

- [ ] All health checks pass
- [ ] API endpoints respond correctly
- [ ] Transformations produce expected output
- [ ] Performance meets requirements
- [ ] Error handling works properly
- [ ] Cluster can handle expected load
- [ ] Monitoring and logging are functional
- [ ] Cleanup procedures work correctly

---

**Happy Testing! 🚀**
