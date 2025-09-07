package com.sparktransform.sparkjob;

import com.sparktransform.dsl.DSLExecutor;
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
            
            // Cache the input DataFrame for better performance if it will be used multiple times
            long inputCount = inputDF.count();
            System.out.println("Processing " + inputCount + " records");
            
            if (inputCount > 100000) { // Cache large datasets
                inputDF.cache();
            }
            
            // Apply DSL transformations using optimized DataFrame operations
            Dataset<Row> outputDF = applyDSLTransformations(inputDF, dslScript);
            
            // Cache output if it's large and will be used multiple times
            if (inputCount > 100000) {
                outputDF.cache();
            }
            
            // Write output with optimized partitioning
            long recordCount = outputDF.count();
            if (recordCount < 1000000) {
                outputDF.coalesce(1)
                       .write()
                       .mode("overwrite")
                       .option("header", "true")
                       .csv(outputPath);
            } else {
                // Use optimal number of output files (typically 1 file per 100MB)
                int outputPartitions = Math.max(1, (int) Math.ceil(recordCount / 100000.0));
                outputDF.coalesce(outputPartitions)
                       .write()
                       .mode("overwrite")
                       .option("header", "true")
                       .csv(outputPath);
            }
            
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
    

    private Dataset<Row> applyDSLTransformations(Dataset<Row> inputDF, String dslScript) {
        System.out.println("Applying DSL transformations using simple DataFrame operations");
        
        // Parse DSL script to understand what transformations to apply
        DSLExecutor dslExecutor = new DSLExecutor();
        
        // Validate DSL script - fail fast if invalid
        if (!dslExecutor.validateDSL(dslScript)) {
            throw new IllegalArgumentException("Invalid DSL script provided: " + dslScript);
        }
        
        // Convert to RDD for DSL processing
        org.apache.spark.api.java.JavaRDD<Row> inputRDD = inputDF.javaRDD();
        
        // Apply DSL transformations using simple map operation
        org.apache.spark.api.java.JavaRDD<Row> transformedRDD = inputRDD.map(
            new SimpleDSLMapFunction(inputDF.columns(), dslScript)
        );
        
        // Convert back to DataFrame
        return inputDF.sparkSession().createDataFrame(transformedRDD, inputDF.schema());
    }

    /**
     * Simple DSL Map Function that was working before optimization
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
            
            // Convert transformed map back to Row
            return convertMapToRow(transformedRow, columnNames);
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
