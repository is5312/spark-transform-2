package com.sparktransform.sparkjob;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Singleton Spring Context Manager for Spark partition processing
 * Ensures Spring context is initialized only once per JVM to optimize performance
 */
public class SpringContextManager {
    
    private static volatile SpringContextManager instance;
    private static final ReentrantLock lock = new ReentrantLock();
    
    private volatile ApplicationContext springContext;
    private volatile boolean initialized = false;
    private volatile boolean initializationFailed = false;
    
    /**
     * Private constructor for singleton pattern
     */
    private SpringContextManager() {
    }
    
    /**
     * Get singleton instance using double-checked locking
     */
    public static SpringContextManager getInstance() {
        if (instance == null) {
            lock.lock();
            try {
                if (instance == null) {
                    instance = new SpringContextManager();
                }
            } finally {
                lock.unlock();
            }
        }
        return instance;
    }
    
    /**
     * Get Spring Application Context, initializing if necessary
     */
    public ApplicationContext getSpringContext() {
        if (!initialized && !initializationFailed) {
            initializeSpringContext();
        }
        return springContext;
    }
    
    /**
     * Check if Spring context is available and initialized
     */
    public boolean isSpringContextAvailable() {
        return initialized && springContext != null;
    }
    
    /**
     * Initialize Spring Application Context with AutoConfiguration (thread-safe)
     */
    private void initializeSpringContext() {
        if (initialized || initializationFailed) {
            return;
        }
        
        lock.lock();
        try {
            // Double-check pattern
            if (initialized || initializationFailed) {
                return;
            }
            
            System.out.println("🚀 SpringContextManager: Initializing singleton Spring context...");
            long startTime = System.currentTimeMillis();
            
            try {
                // Create annotation-based application context
                AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                
                // Set up environment and property sources for AutoConfiguration
                configureEnvironmentForAutoConfiguration(context);
                
                // Configure component scanning for the specified package
                context.scan("com.ssc.isvc");
                
                // Register AutoConfiguration classes
                registerAutoConfigurationClasses(context);
                
                // Register additional configuration classes
                context.register(PartitionSpringConfiguration.class);
                context.register(AutoConfigurationEnablerConfiguration.class);
                
                // Refresh context to initialize all beans (this triggers AutoConfiguration)
                context.refresh();
                
                // Validate that critical beans are available
                validateSpringBeans(context);
                
                this.springContext = context;
                this.initialized = true;
                
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("✅ SpringContextManager: Context initialized successfully in " + 
                    duration + "ms with " + context.getBeanDefinitionCount() + " beans");
                
                // Register shutdown hook for proper cleanup
                registerShutdownHook(context);
                
            } catch (Exception e) {
                System.err.println("❌ SpringContextManager: Failed to initialize Spring context: " + e.getMessage());
                e.printStackTrace();
                this.initializationFailed = true;
                this.springContext = null;
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Configure environment and property sources for AutoConfiguration
     */
    private void configureEnvironmentForAutoConfiguration(AnnotationConfigApplicationContext context) {
        // Set active profiles for different environments
        String activeProfiles = System.getProperty("spring.profiles.active", "partition,autoconfigure");
        String[] profiles = activeProfiles.split(",");
        for (int i = 0; i < profiles.length; i++) {
            profiles[i] = profiles[i].trim();
        }
        context.getEnvironment().setActiveProfiles(profiles);
        
        // Add property sources for AutoConfiguration
        MutablePropertySources propertySources = context.getEnvironment().getPropertySources();
        
        // Add system properties first (highest priority)
        propertySources.addLast(new SystemEnvironmentPropertySource(
            "systemEnvironment", (java.util.Map<String, Object>) (java.util.Map<?, ?>) System.getenv()));
        
        // Load properties from application.yml (via properties files as fallback)
        Properties applicationProps = loadApplicationProperties();
        if (!applicationProps.isEmpty()) {
            propertySources.addFirst(new PropertiesPropertySource("applicationProperties", applicationProps));
            System.out.println("✅ SpringContextManager: Loaded " + applicationProps.size() + " properties from application configuration");
        }
        
        // Add any additional runtime properties
        Properties runtimeProps = getRuntimeProperties();
        if (!runtimeProps.isEmpty()) {
            propertySources.addFirst(new PropertiesPropertySource("runtimeProperties", runtimeProps));
        }
        
        System.out.println("✅ SpringContextManager: Environment configured with profiles: " + java.util.Arrays.toString(profiles));
    }
    
    /**
     * Load properties from application.yml or fallback property files
     */
    private Properties loadApplicationProperties() {
        Properties properties = new Properties();
        
        try {
            // Try to load application.yml properties first (converted to .properties format)
            // Note: In a full Spring Boot application, this would be handled automatically
            // For this standalone context, we'll provide essential properties
            
            // Load from application.properties if it exists
            ClassPathResource appPropsResource = new ClassPathResource("application.properties");
            if (appPropsResource.exists()) {
                Properties appProps = PropertiesLoaderUtils.loadProperties(appPropsResource);
                properties.putAll(appProps);
                System.out.println("✅ SpringContextManager: Loaded application.properties");
            }
            
            // Load profile-specific properties
            String activeProfiles = System.getProperty("spring.profiles.active", "partition,autoconfigure");
            for (String profile : activeProfiles.split(",")) {
                profile = profile.trim();
                ClassPathResource profileResource = new ClassPathResource("application-" + profile + ".properties");
                if (profileResource.exists()) {
                    Properties profileProps = PropertiesLoaderUtils.loadProperties(profileResource);
                    properties.putAll(profileProps);
                    System.out.println("✅ SpringContextManager: Loaded application-" + profile + ".properties");
                }
            }
            
        } catch (IOException e) {
            System.out.println("⚠️ SpringContextManager: Could not load external properties: " + e.getMessage());
            System.out.println("⚠️ SpringContextManager: Falling back to default properties");
        }
        
        // If no external properties found, provide minimal defaults
        if (properties.isEmpty()) {
            properties = getDefaultProperties();
            System.out.println("⚠️ SpringContextManager: Using default fallback properties");
        }
        
        return properties;
    }
    
    /**
     * Get runtime properties that can override configuration
     */
    private Properties getRuntimeProperties() {
        Properties runtimeProps = new Properties();
        
        // Allow environment variables to override settings
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl != null) {
            runtimeProps.setProperty("spring.datasource.url", dbUrl);
        }
        
        String redisHost = System.getenv("REDIS_HOST");
        if (redisHost != null) {
            runtimeProps.setProperty("spring.redis.host", redisHost);
        }
        
        String redisPort = System.getenv("REDIS_PORT");
        if (redisPort != null) {
            runtimeProps.setProperty("spring.redis.port", redisPort);
        }
        
        // Allow system properties to override
        runtimeProps.putAll(System.getProperties());
        
        return runtimeProps;
    }
    
    /**
     * Get default properties as fallback when external configuration is not available
     */
    private Properties getDefaultProperties() {
        Properties defaultProps = new Properties();
        
        // Core Spring settings
        defaultProps.setProperty("spring.autoconfigure", "true");
        defaultProps.setProperty("spring.main.allow-bean-definition-overriding", "true");
        defaultProps.setProperty("spring.main.lazy-initialization", "false");
        
        // Database settings
        defaultProps.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        defaultProps.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/spark_transform");
        defaultProps.setProperty("spring.datasource.username", "spark_user");
        defaultProps.setProperty("spring.datasource.password", "spark_password");
        
        // Redis settings
        defaultProps.setProperty("spring.redis.host", "localhost");
        defaultProps.setProperty("spring.redis.port", "6379");
        
        // Logging settings
        defaultProps.setProperty("logging.level.com.ssc.isvc", "INFO");
        defaultProps.setProperty("logging.level.com.sparktransform", "INFO");
        
        // Custom partition settings
        defaultProps.setProperty("custom.partition.enabled", "true");
        defaultProps.setProperty("custom.partition.environment", "spark-partition");
        defaultProps.setProperty("custom.partition.connection-pool-size", "10");
        defaultProps.setProperty("custom.partition.timeout-ms", "30000");
        
        return defaultProps;
    }
    
    /**
     * Register AutoConfiguration classes manually
     */
    private void registerAutoConfigurationClasses(AnnotationConfigApplicationContext context) {
        try {
            // Register core Spring Boot AutoConfiguration classes
            Class<?>[] autoConfigClasses = {
                // DataSource AutoConfiguration
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class,
                
                // Redis AutoConfiguration  
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
                
                // Transaction AutoConfiguration
                org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration.class,
                
                // Jackson AutoConfiguration
                org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
                
                // Validation AutoConfiguration
                org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class
            };
            
            for (Class<?> autoConfigClass : autoConfigClasses) {
                try {
                    context.register(autoConfigClass);
                    System.out.println("✅ SpringContextManager: Registered AutoConfiguration: " + 
                        autoConfigClass.getSimpleName());
                } catch (Exception e) {
                    System.out.println("⚠️ SpringContextManager: Failed to register " + 
                        autoConfigClass.getSimpleName() + ": " + e.getMessage());
                }
            }
            
            // Load additional AutoConfiguration classes from spring.factories if available
            loadAutoConfigurationFromFactories(context);
            
        } catch (Exception e) {
            System.err.println("❌ SpringContextManager: Error registering AutoConfiguration classes: " + e.getMessage());
        }
    }
    
    /**
     * Load AutoConfiguration classes from META-INF/spring.factories
     */
    private void loadAutoConfigurationFromFactories(AnnotationConfigApplicationContext context) {
        try {
            // Load our custom AutoConfiguration classes from spring.factories
            Properties properties = new Properties();
            InputStream stream = context.getClassLoader().getResourceAsStream("META-INF/spring.factories");
            
            if (stream != null) {
                properties.load(stream);
                stream.close();
                
                String autoConfigClasses = properties.getProperty(
                    "org.springframework.boot.autoconfigure.EnableAutoConfiguration");
                
                if (autoConfigClasses != null) {
                    String[] classNames = autoConfigClasses.split(",");
                    System.out.println("SpringContextManager: Found " + classNames.length + 
                        " custom AutoConfiguration classes in spring.factories");
                    
                    // Register our custom AutoConfiguration classes
                    for (String className : classNames) {
                        className = className.trim();
                        try {
                            if (isSafeAutoConfiguration(className)) {
                                Class<?> autoConfigClass = Class.forName(className, false, context.getClassLoader());
                                context.register(autoConfigClass);
                                System.out.println("✅ SpringContextManager: Loaded custom AutoConfiguration: " + 
                                    autoConfigClass.getSimpleName());
                            }
                        } catch (Exception e) {
                            // Skip problematic AutoConfiguration classes
                            System.out.println("⚠️ SpringContextManager: Skipped " + className + ": " + e.getMessage());
                        }
                    }
                } else {
                    System.out.println("SpringContextManager: No custom AutoConfiguration classes found in spring.factories");
                }
            } else {
                System.out.println("SpringContextManager: No spring.factories file found - using default AutoConfiguration only");
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ SpringContextManager: Could not load AutoConfiguration from spring.factories: " + 
                e.getMessage());
        }
    }
    
    /**
     * Check if an AutoConfiguration class is safe to load in Spark partition context
     */
    private boolean isSafeAutoConfiguration(String className) {
        // First check if this configuration should be excluded
        if (isConfigurationExcluded(className)) {
            return false;
        }
        
        // Only include AutoConfiguration classes that are safe for Spark partitions
        return className.contains("DataSource") ||
               className.contains("Jdbc") ||
               className.contains("Redis") ||
               className.contains("Jackson") ||
               className.contains("Transaction") ||
               className.contains("Validation") ||
               className.contains("Metrics") ||
               className.contains("Health") ||
               className.contains("Cache") ||
               className.contains("com.ssc.isvc"); // Include our custom AutoConfiguration
    }
    
    /**
     * Check if a configuration class should be excluded from loading
     */
    private boolean isConfigurationExcluded(String className) {
        // Use centralized exclusion manager
        return ConfigurationExclusionManager.isConfigurationExcluded(className);
    }
    
    /**
     * Validate that critical Spring beans are properly initialized
     */
    private void validateSpringBeans(ApplicationContext context) {
        try {
            // Check for DataSource (JDBC)
            try {
                javax.sql.DataSource dataSource = context.getBean(javax.sql.DataSource.class);
                System.out.println("✅ SpringContextManager: JDBC DataSource initialized: " + 
                    dataSource.getClass().getSimpleName());
            } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
                System.out.println("⚠️ SpringContextManager: No JDBC DataSource found - database operations not available");
            }
            
            // Check for Redis connections
            try {
                Object redisTemplate = context.getBean("redisTemplate");
                System.out.println("✅ SpringContextManager: Redis Template initialized: " + 
                    redisTemplate.getClass().getSimpleName());
            } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
                System.out.println("⚠️ SpringContextManager: No Redis Template found - Redis operations not available");
            }
            
            // Check for custom auto-configured beans
            try {
                Object partitionDataProcessor = context.getBean("partitionDataProcessor");
                System.out.println("✅ SpringContextManager: Custom PartitionDataProcessor initialized: " + 
                    partitionDataProcessor.getClass().getSimpleName());
            } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
                System.out.println("⚠️ SpringContextManager: No PartitionDataProcessor found");
            }
            
            // Check for gRPC services
            String[] grpcBeans = context.getBeanNamesForType(Object.class);
            long grpcCount = java.util.Arrays.stream(grpcBeans)
                .filter(name -> name.toLowerCase().contains("grpc"))
                .count();
            if (grpcCount > 0) {
                System.out.println("✅ SpringContextManager: gRPC services found: " + grpcCount + " beans");
            } else {
                System.out.println("⚠️ SpringContextManager: No gRPC services found");
            }
            
            // List all service beans
            String[] serviceBeans = context.getBeanNamesForAnnotation(org.springframework.stereotype.Service.class);
            System.out.println("✅ SpringContextManager: Service beans loaded: " + serviceBeans.length);
            
            // List all component beans
            String[] componentBeans = context.getBeanNamesForAnnotation(org.springframework.stereotype.Component.class);
            System.out.println("✅ SpringContextManager: Component beans loaded: " + componentBeans.length);
            
        } catch (Exception e) {
            System.err.println("❌ SpringContextManager: Error validating Spring beans: " + e.getMessage());
        }
    }
    
    /**
     * Register shutdown hook for proper Spring context cleanup
     */
    private void registerShutdownHook(ConfigurableApplicationContext context) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("🛑 SpringContextManager: Shutting down Spring context...");
                context.close();
                System.out.println("✅ SpringContextManager: Spring context shutdown complete");
            } catch (Exception e) {
                System.err.println("❌ SpringContextManager: Error during shutdown: " + e.getMessage());
            }
        }));
    }
    
    /**
     * Manually shutdown Spring context (for testing or explicit cleanup)
     */
    public void shutdown() {
        lock.lock();
        try {
            if (initialized && springContext instanceof ConfigurableApplicationContext) {
                System.out.println("🛑 SpringContextManager: Manual shutdown initiated...");
                ((ConfigurableApplicationContext) springContext).close();
                this.springContext = null;
                this.initialized = false;
                this.initializationFailed = false;
                System.out.println("✅ SpringContextManager: Manual shutdown complete");
            }
        } catch (Exception e) {
            System.err.println("❌ SpringContextManager: Error during manual shutdown: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get initialization status information
     */
    public String getStatusInfo() {
        if (initialized) {
            return "✅ Initialized with " + 
                ((AnnotationConfigApplicationContext) springContext).getBeanDefinitionCount() + " beans";
        } else if (initializationFailed) {
            return "❌ Initialization failed";
        } else {
            return "⏳ Not initialized";
        }
    }
}
