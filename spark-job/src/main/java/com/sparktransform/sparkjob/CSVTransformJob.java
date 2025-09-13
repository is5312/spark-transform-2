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
            // Get singleton Spring context (initialized only once per JVM)
            org.springframework.context.ApplicationContext springContext = 
                SpringContextManager.getInstance().getSpringContext();
            
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
            
            return transformedRows.iterator();
        }
        
        /**
         * Get Spring context status for monitoring
         */
        private String getSpringContextStatus() {
            return SpringContextManager.getInstance().getStatusInfo();
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
         * Note: Spring context cleanup is now handled by the singleton SpringContextManager
         * No need for per-partition cleanup since context is shared across all partitions
         */
        
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
    
    // Configuration classes moved to separate files:
    // - PartitionSpringConfiguration.java
    // - AutoConfigurationEnablerConfiguration.java

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
