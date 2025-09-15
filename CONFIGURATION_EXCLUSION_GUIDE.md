# Spring Configuration Exclusion Guide

This guide demonstrates how to programmatically exclude Spring `@Configuration` classes from libraries without modifying the library code itself.

## Problem Statement

When using third-party libraries in Spring applications, especially in Spark environments, you may encounter `@Configuration` classes that:
- Cause conflicts with your application configuration
- Are incompatible with your runtime environment (e.g., Spark partitions)
- Include unwanted auto-configurations
- Fail to initialize properly in your context

## Solutions Overview

### 1. AutoConfigurationExcludeFilter (Recommended)
**File:** `AutoConfigurationEnablerConfiguration.java`

Uses Spring's built-in `AutoConfigurationExcludeFilter` to exclude configurations during auto-configuration scanning.

```java
@Bean
public AutoConfigurationExcludeFilter autoConfigurationExcludeFilter() {
    return new AutoConfigurationExcludeFilter() {
        @Override
        public boolean match(MetadataReader metadataReader,
                           MetadataReaderFactory metadataReaderFactory) throws IOException {
            String className = metadataReader.getClassMetadata().getClassName();
            return ConfigurationExclusionManager.isConfigurationExcluded(className);
        }
    };
}
```

### 2. Centralized Exclusion Manager
**File:** `ConfigurationExclusionManager.java`

Provides a centralized utility for managing all configuration exclusions:

```java
// Exclude specific configuration classes
ConfigurationExclusionManager.addExcludedConfiguration("com.library.ProblematicConfig");

// Exclude entire packages
ConfigurationExclusionManager.addExcludedPackage("com.problematic.library");

// Exclude by pattern
ConfigurationExclusionManager.addExcludedPattern("SecurityConfig");

// Check if configuration should be excluded
boolean excluded = ConfigurationExclusionManager.isConfigurationExcluded(className);
```

### 3. SpringContextManager Integration
**File:** `SpringContextManager.java`

Integrates exclusion logic into the custom Spring context initialization:

```java
private boolean isSafeAutoConfiguration(String className) {
    // Check exclusions first
    if (isConfigurationExcluded(className)) {
        return false;
    }
    // Then check if it's safe for Spark environment
    return className.contains("DataSource") || /* other safe patterns */;
}
```

## Usage Examples

### Basic Exclusion
```java
// Before Spring context initialization
ConfigurationExclusionManager.addExcludedConfiguration("com.library.UnwantedConfig");

// Initialize Spring context - the configuration will be automatically excluded
SpringContextManager.getInstance().getSpringContext();
```

### Package-Level Exclusion
```java
// Exclude all configurations from a problematic library
ConfigurationExclusionManager.addExcludedPackage("com.problematic.library");
```

### Pattern-Based Exclusion
```java
// Exclude all security-related configurations
ConfigurationExclusionManager.addExcludedPattern("SecurityConfig");

// Exclude all web-related configurations
ConfigurationExclusionManager.addExcludedPattern("WebConfig");
```

### Runtime Management
```java
// Add exclusion at runtime
ConfigurationExclusionManager.addExcludedConfiguration("com.library.BadConfig");

// Remove exclusion
ConfigurationExclusionManager.removeExcludedConfiguration("com.library.BadConfig");

// Check current exclusions
Set<String> excluded = ConfigurationExclusionManager.getExcludedConfigurations();
```

## Configuration Options

### 1. Static Configuration (Pre-defined)
Exclusions are defined in `ConfigurationExclusionManager` class:

```java
private static final Set<String> EXCLUDED_CONFIGURATIONS = new HashSet<>(Arrays.asList(
    "com.some.library.UnwantedConfiguration",
    "com.another.library.ProblematicConfig"
));
```

### 2. Property-Based Configuration
Use Spring properties to configure exclusions:

```properties
# application.properties
spring.autoconfigure.exclude=com.library.UnwantedConfig,com.library.AnotherConfig
custom.exclusions.packages=com.problematic.library,com.another.problematic
custom.exclusions.patterns=SecurityConfig,WebConfig
```

### 3. Environment-Specific Exclusions
```java
public static void setupEnvironmentSpecificExclusions() {
    String environment = System.getProperty("spring.profiles.active", "default");
    
    switch (environment) {
        case "spark":
            ConfigurationExclusionManager.addExcludedPattern("WebConfig");
            ConfigurationExclusionManager.addExcludedPattern("SecurityConfig");
            break;
        case "test":
            // Minimal exclusions for testing
            break;
    }
}
```

## Integration Points

### 1. @EnableAutoConfiguration exclude
```java
@EnableAutoConfiguration(exclude = {
    WebMvcAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class PartitionSpringConfiguration {
    // Configuration beans
}
```

### 2. AutoConfigurationExcludeFilter
```java
@Bean
public AutoConfigurationExcludeFilter autoConfigurationExcludeFilter() {
    return (metadataReader, metadataReaderFactory) -> {
        String className = metadataReader.getClassMetadata().getClassName();
        return ConfigurationExclusionManager.isConfigurationExcluded(className);
    };
}
```

### 3. Custom ComponentScan Filters
```java
@ComponentScan(
    basePackages = "com.your.package",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.CUSTOM,
        classes = CustomConfigurationExclusionFilter.class
    )
)
```

## Debugging and Monitoring

### Print Exclusion Summary
```java
ConfigurationExclusionManager.printExclusionSummary();
```

### Monitor Exclusions During Startup
The system automatically logs excluded configurations:
```
🚫 ConfigurationExclusionManager: Excluding configuration [exact match]: com.library.UnwantedConfig
🚫 ConfigurationExclusionManager: Excluding configuration [package match]: com.problematic.library.SomeConfig
```

### Validate Exclusions
```java
// Check if a specific configuration is excluded
boolean excluded = ConfigurationExclusionManager.isConfigurationExcluded("com.library.Config");

// Get all current exclusions
Set<String> configurations = ConfigurationExclusionManager.getExcludedConfigurations();
Set<String> packages = ConfigurationExclusionManager.getExcludedPackages();
Set<String> patterns = ConfigurationExclusionManager.getExcludedPatterns();
```

## Best Practices

### 1. Exclusion Strategy
- **Exact Match**: Use for specific known problematic configurations
- **Package Match**: Use to exclude entire libraries
- **Pattern Match**: Use for types of configurations (e.g., all SecurityConfig classes)

### 2. Testing
```java
@Test
public void testConfigurationExclusion() {
    ConfigurationExclusionManager.addExcludedConfiguration("com.test.BadConfig");
    assertTrue(ConfigurationExclusionManager.isConfigurationExcluded("com.test.BadConfig"));
    
    ConfigurationExclusionManager.removeExcludedConfiguration("com.test.BadConfig");
    assertFalse(ConfigurationExclusionManager.isConfigurationExcluded("com.test.BadConfig"));
}
```

### 3. Environment Safety
```java
// Create safe Spark environment
public static void createSafeSparkEnvironment() {
    ConfigurationExclusionManager.addExcludedPattern("WebConfig");
    ConfigurationExclusionManager.addExcludedPattern("ServletConfig");
    ConfigurationExclusionManager.addExcludedPattern("SecurityConfig");
    ConfigurationExclusionManager.addExcludedPattern("ActuatorConfig");
}
```

### 4. Cleanup
```java
// Clear exclusions when needed (e.g., in tests)
ConfigurationExclusionManager.clearAllExclusions();
```

## Advanced Scenarios

### 1. Conditional Exclusion Based on Classpath
```java
public static boolean shouldExcludeConfiguration(String className) {
    // Only exclude if certain classes are present
    try {
        Class.forName("org.apache.spark.SparkContext");
        return className.contains("WebConfig"); // Exclude web configs in Spark environment
    } catch (ClassNotFoundException e) {
        return false; // Don't exclude in non-Spark environment
    }
}
```

### 2. Dynamic Exclusion Based on Properties
```java
@PostConstruct
public void configureExclusions() {
    String excludedConfigs = environment.getProperty("custom.exclusions.configurations", "");
    if (!excludedConfigs.isEmpty()) {
        Arrays.stream(excludedConfigs.split(","))
              .forEach(ConfigurationExclusionManager::addExcludedConfiguration);
    }
}
```

### 3. Integration with Spring Profiles
```java
@Profile("spark")
@Component
public class SparkExclusionConfiguration {
    
    @PostConstruct
    public void configureSparkExclusions() {
        ConfigurationExclusionManager.addExcludedPattern("WebConfig");
        ConfigurationExclusionManager.addExcludedPattern("SecurityConfig");
    }
}
```

## Summary

This configuration exclusion system provides multiple layers of control:

1. **Static exclusions** for known problematic configurations
2. **Dynamic exclusions** for runtime control
3. **Pattern-based exclusions** for broad categories
4. **Environment-specific exclusions** for different deployment scenarios
5. **Centralized management** for consistency across the application

The system integrates seamlessly with your existing `SpringContextManager` and `AutoConfigurationEnablerConfiguration` classes, providing a robust solution for managing library configuration conflicts in Spring applications.
