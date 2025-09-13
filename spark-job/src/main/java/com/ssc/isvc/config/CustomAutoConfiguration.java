package com.ssc.isvc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Custom AutoConfiguration class for partition-level Spring context
 * This demonstrates how to create AutoConfiguration classes that will be
 * automatically triggered during Spring context initialization in Spark partitions
 */
@AutoConfiguration
@EnableConfigurationProperties(CustomAutoConfiguration.CustomProperties.class)
@ConditionalOnProperty(name = "spring.autoconfigure", havingValue = "true", matchIfMissing = true)
public class CustomAutoConfiguration {
    
    /**
     * Configuration properties for custom services
     */
    @ConfigurationProperties(prefix = "custom.partition")
    public static class CustomProperties {
        private boolean enabled = true;
        private String environment = "spark-partition";
        private int connectionPoolSize = 10;
        private long timeoutMs = 30000;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        
        public int getConnectionPoolSize() { return connectionPoolSize; }
        public void setConnectionPoolSize(int connectionPoolSize) { this.connectionPoolSize = connectionPoolSize; }
        
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }
    
    /**
     * Auto-configured data processor bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "partitionDataProcessor")
    @ConditionalOnProperty(name = "custom.partition.enabled", havingValue = "true", matchIfMissing = true)
    public PartitionDataProcessor partitionDataProcessor(CustomProperties properties) {
        System.out.println("🚀 AutoConfiguration: Creating PartitionDataProcessor with pool size: " + 
            properties.getConnectionPoolSize());
        return new PartitionDataProcessor(properties);
    }
    
    /**
     * Auto-configured connection manager
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "javax.sql.DataSource")
    public PartitionConnectionManager partitionConnectionManager(CustomProperties properties) {
        System.out.println("🚀 AutoConfiguration: Creating PartitionConnectionManager for environment: " + 
            properties.getEnvironment());
        return new PartitionConnectionManager(properties);
    }
    
    /**
     * Auto-configured cache manager (only if Redis is available)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    @ConditionalOnProperty(name = "spring.redis.host")
    public PartitionCacheManager partitionCacheManager(CustomProperties properties) {
        System.out.println("🚀 AutoConfiguration: Creating PartitionCacheManager with timeout: " + 
            properties.getTimeoutMs() + "ms");
        return new PartitionCacheManager(properties);
    }
    
    /**
     * Custom processor for partition-level data processing
     */
    public static class PartitionDataProcessor {
        private final CustomProperties properties;
        
        public PartitionDataProcessor(CustomProperties properties) {
            this.properties = properties;
        }
        
        public String processData(String input) {
            return "PROCESSED[" + properties.getEnvironment() + "]:" + input;
        }
        
        public CustomProperties getProperties() {
            return properties;
        }
    }
    
    /**
     * Connection manager for partition-level database connections
     */
    public static class PartitionConnectionManager {
        private final CustomProperties properties;
        
        public PartitionConnectionManager(CustomProperties properties) {
            this.properties = properties;
            initializeConnections();
        }
        
        private void initializeConnections() {
            System.out.println("📡 Initializing " + properties.getConnectionPoolSize() + 
                " connections for partition processing");
        }
        
        public String getConnectionInfo() {
            return "Pool Size: " + properties.getConnectionPoolSize() + 
                   ", Environment: " + properties.getEnvironment();
        }
    }
    
    /**
     * Cache manager for partition-level caching
     */
    public static class PartitionCacheManager {
        private final CustomProperties properties;
        private final java.util.Map<String, Object> localCache = new java.util.concurrent.ConcurrentHashMap<>();
        
        public PartitionCacheManager(CustomProperties properties) {
            this.properties = properties;
        }
        
        public void put(String key, Object value) {
            localCache.put(key, value);
        }
        
        public Object get(String key) {
            return localCache.get(key);
        }
        
        public String getCacheInfo() {
            return "Cache entries: " + localCache.size() + 
                   ", Timeout: " + properties.getTimeoutMs() + "ms";
        }
    }
    
    /**
     * Configuration class for additional beans
     */
    @Configuration
    @ConditionalOnProperty(name = "custom.partition.advanced.enabled", havingValue = "true")
    public static class AdvancedConfiguration {
        
        @Bean
        @Primary
        public AdvancedPartitionProcessor advancedPartitionProcessor() {
            System.out.println("🚀 AutoConfiguration: Creating AdvancedPartitionProcessor");
            return new AdvancedPartitionProcessor();
        }
    }
    
    /**
     * Advanced processor with additional capabilities
     */
    public static class AdvancedPartitionProcessor {
        
        public String processAdvanced(String input) {
            return "ADVANCED_PROCESSED:" + input.toUpperCase();
        }
        
        public boolean validateInput(String input) {
            return input != null && !input.trim().isEmpty();
        }
    }
}
