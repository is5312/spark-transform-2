# Spring Profiles and Configuration Guide

This guide explains how to use Spring profiles and external configuration files instead of hardcoded properties in your Spark job module.

## Overview

The `SpringContextManager` has been updated to load configuration from external files rather than hardcoded values. This provides better flexibility, environment-specific settings, and easier maintenance.

## Configuration Files Structure

```
spark-job/src/main/resources/
├── application.yml              # Main YAML configuration (recommended)
├── application.properties       # Main properties file (fallback)
├── application-partition.properties
├── application-autoconfigure.properties  
├── application-dev.properties
├── application-prod.properties
└── META-INF/
    └── spring.factories
```

## Available Profiles

### 1. **Default Profile**
Base configuration used when no specific profile is active.

```yaml
spring:
  application:
    name: spark-transform-job
  main:
    allow-bean-definition-overriding: true
    lazy-initialization: false
  autoconfigure: true
```

### 2. **Partition Profile** (`partition`)
Optimized for Spark partition processing.

**Activation:**
```bash
-Dspring.profiles.active=partition
```

**Key Settings:**
- Enhanced connection pooling (20 connections)
- Optimized timeouts (45 seconds)
- Batch processing settings
- Debug logging for partition operations

### 3. **AutoConfigure Profile** (`autoconfigure`)
Specialized for auto-configuration scenarios.

**Activation:**
```bash
-Dspring.profiles.active=autoconfigure
```

**Key Settings:**
- Auto-resource detection enabled
- Extended timeouts (60 seconds)
- Debug logging for auto-configuration
- More permissive configuration loading

### 4. **Development Profile** (`dev`)
Development-specific settings with relaxed security and debug features.

**Activation:**
```bash
-Dspring.profiles.active=dev
```

**Key Settings:**
- Development database configuration
- Separate Redis database (database=1)
- Debug logging enabled
- Relaxed configuration exclusions
- Smaller connection pools for local development

### 5. **Production Profile** (`prod`)
Production-optimized settings with enhanced performance and security.

**Activation:**
```bash
-Dspring.profiles.active=prod
```

**Key Settings:**
- Environment variable-based configuration
- Large connection pools (50 connections)
- Strict configuration exclusions
- Metrics enabled
- Minimal logging
- Connection leak detection

### 6. **Test Profile** (`test`)
Testing-specific configuration with in-memory databases.

**Activation:**
```bash
-Dspring.profiles.active=test
```

**Key Settings:**
- H2 in-memory database
- Separate Redis database (database=2)
- Minimal configuration exclusions
- Fast timeouts
- Debug logging

### 7. **Docker Profile** (`docker`)
Container-optimized settings.

**Activation:**
```bash
-Dspring.profiles.active=docker
```

**Key Settings:**
- Container-aware hostnames
- Environment variable configuration
- Container-optimized settings

## Usage Examples

### 1. Basic Usage (Default)
```java
// Uses default configuration from application.yml/properties
SpringContextManager.getInstance().getSpringContext();
```

### 2. Single Profile
```bash
# Set profile via system property
java -Dspring.profiles.active=partition -jar your-app.jar

# Or via environment variable
export SPRING_PROFILES_ACTIVE=partition
java -jar your-app.jar
```

### 3. Multiple Profiles
```bash
# Combine profiles
java -Dspring.profiles.active=partition,autoconfigure -jar your-app.jar

# Or
export SPRING_PROFILES_ACTIVE=partition,autoconfigure
```

### 4. Environment-Specific Setup

#### Development Environment
```bash
export SPRING_PROFILES_ACTIVE=dev
export DEV_DB_USERNAME=dev_user
export DEV_DB_PASSWORD=dev_password
java -jar spark-job.jar
```

#### Production Environment
```bash
export SPRING_PROFILES_ACTIVE=prod
export PROD_DB_HOST=prod-database.example.com
export PROD_DB_USERNAME=prod_user
export PROD_DB_PASSWORD=secure_password
export PROD_REDIS_HOST=prod-redis.example.com
java -jar spark-job.jar
```

#### Docker Environment
```bash
docker run -e SPRING_PROFILES_ACTIVE=docker \
           -e POSTGRES_HOST=postgres \
           -e REDIS_HOST=redis \
           your-spark-image
```

## Configuration Properties

### Database Configuration
```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/spark_transform
    username: ${DB_USERNAME:spark_user}
    password: ${DB_PASSWORD:spark_password}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
```

### Redis Configuration
```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    timeout: 2000ms
    database: 0
```

### Custom Partition Settings
```yaml
custom:
  partition:
    enabled: true
    environment: spark-partition
    connection-pool-size: 10
    timeout-ms: 30000
    batch-size: 1000
    max-retries: 3
```

### Configuration Exclusions
```yaml
exclusions:
  configurations:
    - com.some.library.UnwantedConfiguration
    - com.another.library.ProblematicConfig
  packages:
    - com.problematic.library
    - com.unwanted.package
  patterns:
    - SecurityConfig
    - WebConfig
```

## Environment Variables

### Database Environment Variables
- `DATABASE_URL` - Complete database URL
- `DB_USERNAME` - Database username
- `DB_PASSWORD` - Database password
- `POSTGRES_HOST` - PostgreSQL host (Docker)
- `POSTGRES_DB` - PostgreSQL database name (Docker)
- `POSTGRES_USER` - PostgreSQL username (Docker)
- `POSTGRES_PASSWORD` - PostgreSQL password (Docker)

### Redis Environment Variables
- `REDIS_HOST` - Redis host
- `REDIS_PORT` - Redis port
- `REDIS_PASSWORD` - Redis password (if required)

### Production Environment Variables
- `PROD_DB_HOST` - Production database host
- `PROD_DB_NAME` - Production database name
- `PROD_DB_USERNAME` - Production database username
- `PROD_DB_PASSWORD` - Production database password
- `PROD_REDIS_HOST` - Production Redis host
- `PROD_REDIS_PORT` - Production Redis port
- `PROD_REDIS_PASSWORD` - Production Redis password

## Property Precedence

Configuration is loaded in the following order (highest to lowest precedence):

1. **System Properties** (`-Dproperty=value`)
2. **Environment Variables** (`export PROPERTY=value`)
3. **Runtime Properties** (loaded by SpringContextManager)
4. **Profile-specific Properties** (`application-{profile}.properties`)
5. **Main Application Properties** (`application.properties`)
6. **Default Properties** (fallback in SpringContextManager)

## Profile Combinations

### Common Combinations

#### Spark Partition Processing
```bash
SPRING_PROFILES_ACTIVE=partition,autoconfigure
```
- Optimized for partition processing
- Auto-configuration enabled
- Enhanced connection pooling

#### Development with Auto-Configuration
```bash
SPRING_PROFILES_ACTIVE=dev,autoconfigure
```
- Development database settings
- Auto-configuration debugging
- Relaxed exclusions

#### Production with Monitoring
```bash
SPRING_PROFILES_ACTIVE=prod
```
- Production-optimized settings
- Metrics and monitoring enabled
- Strict security exclusions

## Customization

### Adding New Profiles

1. Create a new properties file:
```bash
touch spark-job/src/main/resources/application-myprofile.properties
```

2. Add profile-specific configuration:
```properties
# application-myprofile.properties
custom.partition.environment=my-custom-environment
spring.datasource.url=jdbc:postgresql://my-host:5432/my-db
logging.level.com.sparktransform=DEBUG
```

3. Activate the profile:
```bash
java -Dspring.profiles.active=myprofile -jar your-app.jar
```

### Overriding Configuration

#### Via System Properties
```bash
java -Dspring.datasource.url=jdbc:postgresql://override-host:5432/db \
     -Dcustom.partition.connection-pool-size=25 \
     -jar your-app.jar
```

#### Via Environment Variables
```bash
export DATABASE_URL=jdbc:postgresql://override-host:5432/db
export REDIS_HOST=override-redis-host
java -jar your-app.jar
```

## Integration with SpringContextManager

The `SpringContextManager` automatically:

1. **Loads active profiles** from `spring.profiles.active` system property
2. **Loads properties** from `application.properties` and profile-specific files
3. **Applies environment variables** and system property overrides
4. **Falls back to defaults** if external configuration is not available
5. **Validates configuration** and logs the loading process

### Example Logs
```
✅ SpringContextManager: Loaded application.properties
✅ SpringContextManager: Loaded application-partition.properties
✅ SpringContextManager: Environment configured with profiles: [partition, autoconfigure]
✅ SpringContextManager: Loaded 25 properties from application configuration
```

## Migration from Hardcoded Properties

### Before (Hardcoded)
```java
Properties autoConfigProps = new Properties();
autoConfigProps.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
autoConfigProps.setProperty("spring.redis.host", "localhost");
autoConfigProps.setProperty("custom.partition.enabled", "true");
```

### After (External Configuration)
```yaml
# application.yml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
  redis:
    host: localhost

custom:
  partition:
    enabled: true
```

The SpringContextManager now automatically loads these properties based on the active profile.

## Troubleshooting

### Configuration Not Loading
1. Check if the properties file exists in the classpath
2. Verify the profile name matches the file suffix
3. Check for syntax errors in YAML/properties files
4. Enable debug logging: `-Dlogging.level.com.sparktransform.sparkjob=DEBUG`

### Profile Not Active
1. Verify the system property: `echo $SPRING_PROFILES_ACTIVE`
2. Check for typos in profile names
3. Ensure multiple profiles are comma-separated
4. Check application logs for profile activation messages

### Environment Variables Not Working
1. Verify variable names match the expected format
2. Check for shell export: `export VARIABLE_NAME=value`
3. Restart the application after setting variables
4. Use debug logging to see loaded properties

This configuration system provides much better flexibility and maintainability compared to hardcoded properties!
