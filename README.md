# Spark Transform Cluster

A distributed Spark cluster for processing large CSV files (up to 500GB) using custom DSL transformations.

## Architecture

- **1 Spark Master**: Coordinates the cluster and manages job scheduling
- **3 Spark Workers**: Execute the actual data processing tasks
- **1 History Server**: Provides web UI for monitoring completed jobs
- **Spring Boot API**: RESTful interface for submitting transformation jobs

## Project Structure

```
spark-transform-2/
├── dsl-library/           # Custom DSL execution engine
├── spark-job/            # Spark job implementation
├── spring-boot-app/      # REST API for job submission
├── docker-compose.yml    # Docker cluster configuration
├── Dockerfile           # Custom Spark image with DSL library
├── build.gradle         # Gradle multi-module configuration
└── settings.gradle      # Gradle project settings
```

## Prerequisites

- Docker and Docker Compose
- Java 17
- Gradle 8.4+ (or use the included Gradle wrapper)

## Quick Start

### 1. Build the Project

```bash
# Build all modules
./gradlew clean build

# Build Docker image
docker build -t custom-spark:latest .
```

### 2. Start the Cluster

```bash
# Start all services
docker-compose up -d

# Check cluster status
docker-compose ps
```

### 3. Access Web UIs

- **Spark Master**: http://localhost:8080
- **Worker 1**: http://localhost:8081
- **Worker 2**: http://localhost:8082
- **Worker 3**: http://localhost:8083
- **History Server**: http://localhost:18080
- **Spring Boot API**: http://localhost:8084

## API Usage

### Submit a Transformation Job

```bash
curl -X POST http://localhost:8084/api/transform/submit \
  -H "Content-Type: application/json" \
  -d '{
    "inputPath": "/opt/data/input.csv",
    "outputPath": "/opt/data/output",
    "dslScript": "{\"transformations\":[{\"target\":\"full_name\",\"operation\":\"concat\",\"sources\":[\"first_name\",\" \",\"last_name\"]}]}",
    "jobId": "job-001"
  }'
```

### Check Job Status

```bash
curl http://localhost:8084/api/transform/status/job-001
```

### Kill a Job

```bash
curl -X DELETE http://localhost:8084/api/transform/kill/job-001
```

## DSL Script Format

The DSL script is a JSON configuration that defines transformations to apply to each row:

```json
{
  "transformations": [
    {
      "target": "full_name",
      "operation": "concat",
      "sources": ["first_name", " ", "last_name"]
    },
    {
      "target": "email_upper",
      "operation": "uppercase",
      "source": "email"
    },
    {
      "target": "total_price",
      "operation": "multiply",
      "sources": ["price", "quantity"]
    }
  ]
}
```

### Supported Operations

- **copy**: Copy value from source column
- **concat**: Concatenate multiple values
- **uppercase**: Convert to uppercase
- **lowercase**: Convert to lowercase
- **add**: Add numeric values
- **multiply**: Multiply numeric values
- **conditional**: Apply conditional logic
- **constant**: Set a constant value

## Data Directory

Place your input CSV files in the `./data` directory. The cluster will have access to this directory at `/opt/data`.

## Configuration

### Spark Configuration

Key Spark settings can be modified in `docker-compose.yml`:

- Worker memory: `SPARK_WORKER_MEMORY=4g`
- Worker cores: `SPARK_WORKER_CORES=2`
- Driver memory: `SPARK_DRIVER_MEMORY=2g`

### Spring Boot Configuration

Application settings are in `spring-boot-app/src/main/resources/application.yml`:

```yaml
spark:
  master:
    url: spark://spark-master:7077
  driver:
    memory: 2g
  executor:
    memory: 4g
    cores: 2
```

## Monitoring

### Spark Web UIs

- Monitor active jobs and cluster resources
- View job execution details and logs
- Check worker status and resource utilization

### Application Logs

```bash
# View Spring Boot logs
docker-compose logs -f spring-boot-app

# View Spark master logs
docker-compose logs -f spark-master

# View worker logs
docker-compose logs -f spark-worker-1
```

## Performance Tuning

For processing 500GB files efficiently:

1. **Increase worker memory**: Set `SPARK_WORKER_MEMORY=8g` or higher
2. **Adjust executor cores**: Increase `SPARK_WORKER_CORES` based on your hardware
3. **Enable adaptive query execution**: Already configured in the job
4. **Use appropriate partitioning**: The job uses `coalesce(1)` for single output file

## Troubleshooting

### Common Issues

1. **Out of Memory**: Increase worker memory in docker-compose.yml
2. **Job Submission Fails**: Check if Spark master is running and accessible
3. **File Not Found**: Ensure input files are in the `./data` directory
4. **DSL Validation Errors**: Check JSON syntax in your DSL script

### Debug Mode

Enable debug logging by setting:

```yaml
logging:
  level:
    com.sparktransform: DEBUG
```

## Development

### Gradle Commands

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :dsl-library:build
./gradlew :spark-job:build
./gradlew :spring-boot-app:build

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Create shadow JARs (fat JARs with dependencies)
./gradlew shadowJar
```

### Adding New DSL Operations

1. Extend `DSLExecutor.java` in the `dsl-library` module
2. Add the new operation to the `executeOperation` method
3. Rebuild and redeploy the cluster

### Custom Transformations

The DSL library can be extended with more complex transformation logic as needed for your specific use cases.

## License

This project is for internal use. Please ensure compliance with your organization's policies.
