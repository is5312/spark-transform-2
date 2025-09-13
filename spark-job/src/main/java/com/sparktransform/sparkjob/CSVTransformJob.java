package com.sparktransform.sparkjob;

import com.sparktransform.dsl.DSLExecutor;
import com.sparktransform.dsl.SpringAwareDSLExecutor;
import com.sparktransform.dsl.TransformationContext;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;

import java.util.*;

/**
 * Main Spark job for processing large CSV files with DSL transformations
 */
public class CSVTransformJob implements java.io.Serializable {
    
    private final DSLExecutor dslExecutor;
    
    public CSVTransformJob() {
        this.dslExecutor = new DSLExecutor();
    }
    
    /**
     * Process CSV file with DSL transformations
     * @param sparkSession Spark session to use
     * @param inputPath Path to input CSV file
     * @param outputPath Path to output CSV file
     * @param dslScript DSL transformation script
     * @param jobId Unique job identifier
     * @return true if successful, false otherwise
     */
    public boolean processFile(SparkSession sparkSession, String inputPath, String outputPath, String dslScript, String jobId) {
        try {
            processCSV(sparkSession, inputPath, outputPath, dslScript, jobId);
            return true;
        } catch (Exception e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Process CSV file with DSL transformations
     * @param sparkSession Spark session to use
     * @param inputPath Path to input CSV file
     * @param outputPath Path to output CSV file
     * @param dslScript DSL transformation script
     * @param jobId Unique job identifier
     */
    public void processCSV(SparkSession sparkSession, String inputPath, String outputPath, String dslScript, String jobId) {
        try {
            System.out.println("Starting CSV transformation job: " + jobId);
            System.out.println("Input: " + inputPath + " -> Output: " + outputPath);
            
            // Create transformation context
            TransformationContext context = new TransformationContext(jobId);
            context.setMetadata("inputPath", inputPath);
            context.setMetadata("outputPath", outputPath);
            context.setMetadata("startTime", System.currentTimeMillis());
            
            // Read CSV file
            Dataset<Row> inputDF = readCSV(sparkSession, inputPath);
            
            // Optimize partitioning for large datasets before processing
            Dataset<Row> optimizedInputDF = optimizeDataFrameForProcessing(inputDF);
            
            long inputCount = optimizedInputDF.count();
            System.out.println("Processing " + inputCount + " records");
            
            if (inputCount > 100000) { // Cache large datasets
                optimizedInputDF.cache();
            }
            
            // Apply DSL transformations using optimized DataFrame operations
            Dataset<Row> outputDF = applyDSLTransformations(optimizedInputDF, dslScript);
            
            // Cache output if it's large and will be used multiple times
            if (inputCount > 100000) {
                outputDF.cache();
            }
            
            // Write output with optimized partitioning and performance settings
            long recordCount = outputDF.count();
            writeOutputOptimized(outputDF, outputPath, recordCount);
            
            // Update context with completion info
            context.setMetadata("endTime", System.currentTimeMillis());
            context.setMetadata("recordCount", recordCount);
            
            System.out.println("CSV transformation completed successfully - processed " + recordCount + " records");
            
        } catch (Exception e) {
            System.err.println("Error processing CSV: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("CSV processing failed", e);
        }
    }
    
    /**
     * Read CSV file with optimized settings for large files
     */
    private Dataset<Row> readCSV(SparkSession sparkSession, String inputPath) {
        return sparkSession.read()
                   .option("header", "true")
                   .option("inferSchema", "true")
                   .option("multiline", "true")
                   .option("escape", "\"")
                   .option("timestampFormat", "yyyy-MM-dd HH:mm:ss")
                   .option("dateFormat", "yyyy-MM-dd")
                   .option("maxColumns", "10000")  // Support large number of columns
                   .option("maxCharsPerColumn", "1000000")  // Support large text fields
                   .option("mode", "PERMISSIVE")  // Handle malformed records gracefully
                   .option("columnNameOfCorruptRecord", "_corrupt_record")
                   .csv(inputPath);
    }
    
    /**
     * Optimize DataFrame partitioning for large file processing
     */
    private Dataset<Row> optimizeDataFrameForProcessing(Dataset<Row> inputDF) {
        long recordCount = inputDF.count();
        int currentPartitions = inputDF.rdd().getNumPartitions();
        
        System.out.println("Current partitions: " + currentPartitions + ", Record count: " + recordCount);
        
        // Optimize partitioning based on data size
        if (recordCount > 1000000) {
            // For large datasets, aim for ~100,000-200,000 records per partition
            int optimalPartitions = (int) Math.ceil(recordCount / 150000.0);
            // Cap at reasonable limits to avoid too many small tasks
            optimalPartitions = Math.min(optimalPartitions, 200);
            optimalPartitions = Math.max(optimalPartitions, 2);
            
            if (optimalPartitions != currentPartitions) {
                System.out.println("Repartitioning to " + optimalPartitions + " partitions for optimal processing");
                return inputDF.repartition(optimalPartitions);
            }
        } else if (recordCount > 10000 && currentPartitions > 10) {
            // For medium datasets, reduce excessive partitions
            int optimalPartitions = Math.max(2, (int) Math.ceil(recordCount / 50000.0));
            System.out.println("Reducing partitions to " + optimalPartitions + " for medium dataset");
            return inputDF.coalesce(optimalPartitions);
        }
        
        return inputDF;
    }
    
    /**
     * Optimized output writing with performance tuning for large datasets
     */
    private void writeOutputOptimized(Dataset<Row> outputDF, String outputPath, long recordCount) {
        System.out.println("Writing " + recordCount + " records with optimized settings");
        
        if (recordCount < 50000) {
            // Small datasets: single file
            outputDF.coalesce(1)
                   .write()
                   .mode("overwrite")
                   .option("header", "true")
                   .option("compression", "gzip")  // Compress small files
                   .csv(outputPath);
        } else if (recordCount < 1000000) {
            // Medium datasets: 2-4 files
            int outputPartitions = Math.max(2, Math.min(4, (int) Math.ceil(recordCount / 250000.0)));
            outputDF.coalesce(outputPartitions)
                   .write()
                   .mode("overwrite")
                   .option("header", "true")
                   .option("compression", "gzip")
                   .csv(outputPath);
        } else {
            // Large datasets: optimal partitioning for parallel write performance
            // Aim for ~500MB per output file (roughly 500,000-1,000,000 records)
            int outputPartitions = Math.max(2, (int) Math.ceil(recordCount / 750000.0));
            // Cap to prevent too many small files
            outputPartitions = Math.min(outputPartitions, 50);
            
            System.out.println("Using " + outputPartitions + " output partitions for large dataset");
            
            outputDF.coalesce(outputPartitions)
                   .write()
                   .mode("overwrite")
                   .option("header", "true")
                   .option("compression", "gzip")  // Compress for storage efficiency
                   .csv(outputPath);
        }
    }

    private Dataset<Row> applyDSLTransformations(Dataset<Row> inputDF, String dslScript) {
        System.out.println("Applying DSL transformations using optimized partition-level processing");
        
        // Pre-validate DSL script - fail fast if invalid
        DSLExecutor validationExecutor = new DSLExecutor();
        if (!validationExecutor.validateDSL(dslScript)) {
            throw new IllegalArgumentException("Invalid DSL script provided: " + dslScript);
        }
        
        // Broadcast the DSL script to all executors to avoid network overhead
        org.apache.spark.broadcast.Broadcast<String> broadcastDslScript = 
            inputDF.sparkSession().sparkContext().broadcast(dslScript, scala.reflect.ClassTag$.MODULE$.apply(String.class));
        
        // Broadcast column names for efficient access
        org.apache.spark.broadcast.Broadcast<String[]> broadcastColumnNames = 
            inputDF.sparkSession().sparkContext().broadcast(inputDF.columns(), scala.reflect.ClassTag$.MODULE$.apply(String[].class));
        
        // Convert to RDD for optimized processing
        org.apache.spark.api.java.JavaRDD<Row> inputRDD = inputDF.javaRDD();
        
        // Apply DSL transformations using optimized mapPartitions operation
        org.apache.spark.api.java.JavaRDD<Row> transformedRDD = inputRDD.mapPartitions(
            new OptimizedDSLMapPartitionsFunction(broadcastColumnNames, broadcastDslScript)
        );
        
        return inputDF.sparkSession().createDataFrame(transformedRDD, inputDF.schema());
    }

    /**
     * Optimized DSL MapPartitions Function for high-performance large file processing
     */
    public static class OptimizedDSLMapPartitionsFunction implements org.apache.spark.api.java.function.FlatMapFunction<Iterator<Row>, Row>, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private final org.apache.spark.broadcast.Broadcast<String[]> broadcastColumnNames;
        private final org.apache.spark.broadcast.Broadcast<String> broadcastDslScript;
        
        public OptimizedDSLMapPartitionsFunction(org.apache.spark.broadcast.Broadcast<String[]> broadcastColumnNames, 
                                               org.apache.spark.broadcast.Broadcast<String> broadcastDslScript) {
            this.broadcastColumnNames = broadcastColumnNames;
            this.broadcastDslScript = broadcastDslScript;
        }
        
        @Override
        public Iterator<Row> call(Iterator<Row> partition) throws Exception {
            // Initialize Spring context and dependencies for this partition
            org.springframework.context.ApplicationContext springContext = initializeSpringContext();
            
            // Create single DSL executor per partition (major performance improvement)
            DSLExecutor executor = new DSLExecutor();
            String[] columnNames = broadcastColumnNames.value();
            String dslScript = broadcastDslScript.value();
            
            // Pre-compile DSL rules once per partition
            Map<String, Object> dummyRow = new HashMap<>();
            for (String columnName : columnNames) {
                dummyRow.put(columnName, ""); // Empty values for compilation
            }
            
            // This will cache the compiled rules in the executor
            try {
                executor.executeTransformation(dummyRow, dslScript);
            } catch (Exception e) {
                // Expected to fail with dummy data, but DSL is now compiled and cached
            }
            
            // Process all rows in the partition efficiently with Spring context available
            List<Row> transformedRows = new ArrayList<>();
            
            while (partition.hasNext()) {
                Row row = partition.next();
                
                // Convert Row to Map efficiently
                Map<String, Object> rowMap = new HashMap<>(columnNames.length);
                for (int i = 0; i < columnNames.length; i++) {
                    rowMap.put(columnNames[i], row.get(i));
                }
                
                // Apply DSL transformation with Spring context available
                Map<String, Object> transformedRow = executeTransformationWithSpring(
                    executor, rowMap, dslScript, springContext);
                
                // Apply transformations to original data
                Map<String, Object> resultMap = new HashMap<>(rowMap);
                for (Map.Entry<String, Object> entry : transformedRow.entrySet()) {
                    String targetColumn = entry.getKey();
                    Object transformedValue = entry.getValue();
                    
                    if (resultMap.containsKey(targetColumn)) {
                        resultMap.put(targetColumn, transformedValue);
                    }
                }
                
                // Convert result back to Row efficiently
                transformedRows.add(convertMapToRowOptimized(resultMap, columnNames));
            }
            
            // Cleanup Spring context resources
            cleanupSpringContext(springContext);
            
            return transformedRows.iterator();
        }
        
        /**
         * Initialize Spring Application Context with component scanning and AutoConfiguration
         */
        private org.springframework.context.ApplicationContext initializeSpringContext() {
            try {
                System.out.println("Initializing Spring context with AutoConfiguration for partition...");
                
                // Create annotation-based application context
                org.springframework.context.annotation.AnnotationConfigApplicationContext context = 
                    new org.springframework.context.annotation.AnnotationConfigApplicationContext();
                
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
                
                System.out.println("Spring context initialized successfully with " + 
                    context.getBeanDefinitionCount() + " beans and AutoConfiguration enabled");
                
                return context;
                
            } catch (Exception e) {
                System.err.println("Failed to initialize Spring context: " + e.getMessage());
                e.printStackTrace();
                // Return null context - transformation will proceed without Spring integration
                return null;
            }
        }
        
        /**
         * Configure environment and property sources for AutoConfiguration
         */
        private void configureEnvironmentForAutoConfiguration(
                org.springframework.context.annotation.AnnotationConfigApplicationContext context) {
            
            // Set active profiles for different environments
            context.getEnvironment().setActiveProfiles("partition", "autoconfigure");
            
            // Add property sources for AutoConfiguration
            org.springframework.core.env.MutablePropertySources propertySources = 
                context.getEnvironment().getPropertySources();
            
            // Add system properties
            propertySources.addLast(new org.springframework.core.env.SystemEnvironmentPropertySource(
                "systemEnvironment", (java.util.Map<String, Object>) (java.util.Map<?, ?>) System.getenv()));
            
            // Add custom properties for AutoConfiguration
            java.util.Properties autoConfigProps = new java.util.Properties();
            autoConfigProps.setProperty("spring.autoconfigure", "true");
            autoConfigProps.setProperty("spring.main.allow-bean-definition-overriding", "true");
            autoConfigProps.setProperty("spring.main.lazy-initialization", "false");
            autoConfigProps.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
            autoConfigProps.setProperty("spring.redis.host", "localhost");
            autoConfigProps.setProperty("spring.redis.port", "6379");
            autoConfigProps.setProperty("logging.level.com.ssc.isvc", "INFO");
            
            propertySources.addFirst(new org.springframework.core.env.PropertiesPropertySource(
                "autoConfigProperties", autoConfigProps));
            
            System.out.println("✅ Environment configured for AutoConfiguration");
        }
        
        /**
         * Register AutoConfiguration classes manually
         */
        private void registerAutoConfigurationClasses(
                org.springframework.context.annotation.AnnotationConfigApplicationContext context) {
            
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
                        System.out.println("✅ Registered AutoConfiguration: " + autoConfigClass.getSimpleName());
                    } catch (Exception e) {
                        System.out.println("⚠️ Failed to register " + autoConfigClass.getSimpleName() + 
                            ": " + e.getMessage());
                    }
                }
                
                // Load additional AutoConfiguration classes from spring.factories if available
                loadAutoConfigurationFromFactories(context);
                
            } catch (Exception e) {
                System.err.println("Error registering AutoConfiguration classes: " + e.getMessage());
            }
        }
        
        /**
         * Load AutoConfiguration classes from META-INF/spring.factories
         */
        private void loadAutoConfigurationFromFactories(
                org.springframework.context.annotation.AnnotationConfigApplicationContext context) {
            
            try {
                // Load our custom AutoConfiguration classes from spring.factories
                java.util.Properties properties = new java.util.Properties();
                java.io.InputStream stream = context.getClassLoader()
                    .getResourceAsStream("META-INF/spring.factories");
                
                if (stream != null) {
                    properties.load(stream);
                    stream.close();
                    
                    String autoConfigClasses = properties.getProperty(
                        "org.springframework.boot.autoconfigure.EnableAutoConfiguration");
                    
                    if (autoConfigClasses != null) {
                        String[] classNames = autoConfigClasses.split(",");
                        System.out.println("Found " + classNames.length + 
                            " custom AutoConfiguration classes in spring.factories");
                        
                        // Register our custom AutoConfiguration classes
                        for (String className : classNames) {
                            className = className.trim();
                            try {
                                if (isSafeAutoConfiguration(className)) {
                                    Class<?> autoConfigClass = Class.forName(className, false, context.getClassLoader());
                                    context.register(autoConfigClass);
                                    System.out.println("✅ Loaded custom AutoConfiguration: " + autoConfigClass.getSimpleName());
                                }
                            } catch (Exception e) {
                                // Skip problematic AutoConfiguration classes
                                System.out.println("⚠️ Skipped " + className + ": " + e.getMessage());
                            }
                        }
                    } else {
                        System.out.println("No custom AutoConfiguration classes found in spring.factories");
                    }
                } else {
                    System.out.println("No spring.factories file found - using default AutoConfiguration only");
                }
                
            } catch (Exception e) {
                System.out.println("⚠️ Could not load AutoConfiguration from spring.factories: " + e.getMessage());
            }
        }
        
        /**
         * Check if an AutoConfiguration class is safe to load in Spark partition context
         */
        private boolean isSafeAutoConfiguration(String className) {
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
         * Validate that critical Spring beans are properly initialized
         */
        private void validateSpringBeans(org.springframework.context.ApplicationContext context) {
            try {
                // Check for DataSource (JDBC)
                try {
                    javax.sql.DataSource dataSource = context.getBean(javax.sql.DataSource.class);
                    System.out.println("✅ JDBC DataSource initialized: " + dataSource.getClass().getSimpleName());
                } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
                    System.out.println("⚠️ No JDBC DataSource found - database operations not available");
                }
                
                // Check for Redis connections
                try {
                    Object redisTemplate = context.getBean("redisTemplate");
                    System.out.println("✅ Redis Template initialized: " + redisTemplate.getClass().getSimpleName());
                } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
                    System.out.println("⚠️ No Redis Template found - Redis operations not available");
                }
                
                // Check for gRPC services
                String[] grpcBeans = context.getBeanNamesForType(Object.class);
                long grpcCount = java.util.Arrays.stream(grpcBeans)
                    .filter(name -> name.toLowerCase().contains("grpc"))
                    .count();
                if (grpcCount > 0) {
                    System.out.println("✅ gRPC services found: " + grpcCount + " beans");
                } else {
                    System.out.println("⚠️ No gRPC services found");
                }
                
                // List all service beans
                String[] serviceBeans = context.getBeanNamesForAnnotation(org.springframework.stereotype.Service.class);
                System.out.println("✅ Service beans loaded: " + serviceBeans.length);
                
                // List all component beans
                String[] componentBeans = context.getBeanNamesForAnnotation(org.springframework.stereotype.Component.class);
                System.out.println("✅ Component beans loaded: " + componentBeans.length);
                
            } catch (Exception e) {
                System.err.println("Error validating Spring beans: " + e.getMessage());
            }
        }
        
        /**
         * Execute transformation with Spring context available for dependency injection
         */
        private Map<String, Object> executeTransformationWithSpring(
                DSLExecutor executor, 
                Map<String, Object> rowMap, 
                String dslScript, 
                org.springframework.context.ApplicationContext springContext) {
            
            try {
                // If Spring context is available, inject it into the DSL executor
                if (springContext != null && executor instanceof SpringAwareDSLExecutor) {
                    ((SpringAwareDSLExecutor) executor).setSpringContext(springContext);
                }
                
                // Execute transformation (now with Spring context available)
                return executor.executeTransformation(rowMap, dslScript);
                
            } catch (Exception e) {
                System.err.println("Error in Spring-aware transformation: " + e.getMessage());
                // Fallback to standard transformation
                return executor.executeTransformation(rowMap, dslScript);
            }
        }
        
        /**
         * Cleanup Spring context resources
         */
        private void cleanupSpringContext(org.springframework.context.ApplicationContext springContext) {
            try {
                if (springContext instanceof org.springframework.context.ConfigurableApplicationContext) {
                    ((org.springframework.context.ConfigurableApplicationContext) springContext).close();
                    System.out.println("Spring context cleaned up successfully");
                }
            } catch (Exception e) {
                System.err.println("Error cleaning up Spring context: " + e.getMessage());
            }
        }
        
        /**
         * Optimized map to Row conversion with reduced object allocation
         */
        private Row convertMapToRowOptimized(Map<String, Object> rowMap, String[] columnNames) {
            Object[] values = new Object[columnNames.length];
            for (int i = 0; i < columnNames.length; i++) {
                values[i] = rowMap.get(columnNames[i]);
            }
            return RowFactory.create(values);
        }
    }
    
    /**
     * AutoConfiguration Enabler Configuration
     */
    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.context.annotation.Import({
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
        org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class
    })
    public static class AutoConfigurationEnablerConfiguration {
        
        /**
         * Configure AutoConfiguration exclusions for Spark environment
         */
        @org.springframework.context.annotation.Bean
        public org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter autoConfigurationExcludeFilter() {
            return new org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter() {
                @Override
                public boolean match(org.springframework.core.type.classreading.MetadataReader metadataReader,
                                   org.springframework.core.type.classreading.MetadataReaderFactory metadataReaderFactory)
                        throws java.io.IOException {
                    
                    String className = metadataReader.getClassMetadata().getClassName();
                    
                    // Exclude web-related AutoConfigurations that are not suitable for Spark partitions
                    return className.contains("Web") ||
                           className.contains("Servlet") ||
                           className.contains("Mvc") ||
                           className.contains("Security") ||
                           className.contains("Actuator") ||
                           className.contains("Management") ||
                           className.contains("Jmx");
                }
            };
        }
    }

    /**
     * Spring Configuration for partition-level context
     */
    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.ComponentScan("com.ssc.isvc")
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    })
    public static class PartitionSpringConfiguration {
        
        /**
         * Configure JDBC DataSource if not already present
         */
        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
        public javax.sql.DataSource dataSource() {
            // Configure your database connection here
            org.springframework.boot.jdbc.DataSourceBuilder<?> builder = org.springframework.boot.jdbc.DataSourceBuilder.create();
            
            // Example configuration - replace with your actual database settings
            builder.driverClassName("org.postgresql.Driver");
            builder.url("jdbc:postgresql://localhost:5432/your_database");
            builder.username("your_username");
            builder.password("your_password");
            
            return builder.build();
        }
        
        /**
         * Configure Redis Template if not already present
         */
        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
        public org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate() {
            org.springframework.data.redis.core.RedisTemplate<String, Object> template = 
                new org.springframework.data.redis.core.RedisTemplate<>();
            
            // Configure Redis connection factory
            org.springframework.data.redis.connection.jedis.JedisConnectionFactory factory = 
                new org.springframework.data.redis.connection.jedis.JedisConnectionFactory();
            factory.setHostName("localhost");
            factory.setPort(6379);
            factory.afterPropertiesSet();
            
            template.setConnectionFactory(factory);
            template.setDefaultSerializer(new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer());
            
            return template;
        }
    }

    /**
     * Simple DSL Map Function that was working before optimization (kept for reference)
     */
    public static class SimpleDSLMapFunction implements org.apache.spark.api.java.function.Function<Row, Row>, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String[] columnNames;
        private final String dslScript;
        
        public SimpleDSLMapFunction(String[] columnNames, String dslScript) {
            this.columnNames = columnNames;
            this.dslScript = dslScript;
        }
        
        @Override
        public Row call(Row row) throws Exception {
            // Create DSL executor per row (simple approach)
            DSLExecutor executor = new DSLExecutor();
            
            // Convert Row to Map for DSL processing
            Map<String, Object> rowMap = new HashMap<>(columnNames.length);
            for (int i = 0; i < columnNames.length; i++) {
                rowMap.put(columnNames[i], row.get(i));
            }
            
            // Apply DSL transformation
            Map<String, Object> transformedRow = executor.executeTransformation(rowMap, dslScript);
            
            // Apply transformations to original data - update existing columns with transformed values
            Map<String, Object> resultMap = new HashMap<>(rowMap);
            for (Map.Entry<String, Object> entry : transformedRow.entrySet()) {
                String targetColumn = entry.getKey();
                Object transformedValue = entry.getValue();
                
                // If target column exists in original schema, update it
                if (resultMap.containsKey(targetColumn)) {
                    resultMap.put(targetColumn, transformedValue);
                }
                // If it's a new column, we'll ignore it for now since schema is fixed
            }
            
            // Convert result map back to Row
            return convertMapToRow(resultMap, columnNames);
        }
        
        /**
         * Convert map back to Row
         */
        private Row convertMapToRow(Map<String, Object> transformedRow, String[] columnNames) {
            Object[] values = new Object[columnNames.length];
            for (int i = 0; i < columnNames.length; i++) {
                values[i] = transformedRow.get(columnNames[i]);
            }
            return RowFactory.create(values);
        }
    }
    
    /**
     * Get job statistics
     */
    public Map<String, Object> getJobStats(String jobId) {
        // This could be enhanced to read from a persistent store
        Map<String, Object> stats = new HashMap<>();
        stats.put("jobId", jobId);
        stats.put("status", "completed");
        stats.put("timestamp", System.currentTimeMillis());
        return stats;
    }
    
    
    
}
