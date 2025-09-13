package com.sparktransform.sparkjob;

import com.sparktransform.dsl.DSLExecutor;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.*;

/**
 * Standalone Spark driver for CSV transformations using DSL
 * This runs as a separate Spark application, not as part of Spring Boot
 */
public class SparkTransformDriver {
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: SparkTransformDriver <inputPath> <outputPath> <dslScript> <jobId>");
            System.exit(1);
        }
        
        String inputPath = args[0];
        String outputPath = args[1];
        String dslScript = args[2];
        String jobId = args[3];
        
        System.out.println("Starting Spark Transform Driver");
        System.out.println("Job ID: " + jobId);
        System.out.println("Input Path: " + inputPath);
        System.out.println("Output Path: " + outputPath);
        System.out.println("DSL Script: " + dslScript);
        
        SparkSession spark = null;
        try {
            // Set Hadoop security configuration to disable authentication
            System.setProperty("hadoop.security.authentication", "simple");
            System.setProperty("hadoop.security.authorization", "false");
            System.setProperty("java.security.krb5.conf", "/dev/null");
            System.setProperty("java.security.krb5.realm", "");
            System.setProperty("java.security.krb5.kdc", "");
            
            // Java 17 JVM options to fix module access issues + logging configuration
            String jvmOptions = "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
                    "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED " +
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED " +
                    "--add-opens=java.base/java.io=ALL-UNNAMED " +
                    "--add-opens=java.base/java.util=ALL-UNNAMED " +
                    "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED " +
                    "--add-opens=java.base/java.nio=ALL-UNNAMED " +
                    "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED " +
                    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED " +
                    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED " +
                    "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED " +
                    "--add-opens=java.base/java.net=ALL-UNNAMED " +
                    "--add-opens=java.base/java.nio.channels=ALL-UNNAMED " +
                    "--add-opens=java.base/java.nio.channels.spi=ALL-UNNAMED " +
                    "--add-opens=java.base/sun.security.action=ALL-UNNAMED " +
                    "--add-opens=java.base/sun.util=ALL-UNNAMED " +
                    "-Dlog4j.configurationFile=log4j2.properties " +
                    "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn";

            // Create Spark session with Kryo serialization to handle SerializedLambda issues
            spark = SparkSession.builder()
                    .appName("CSVTransform-" + jobId)
                    .config("spark.hadoop.hadoop.security.authentication", "simple")
                    .config("spark.hadoop.hadoop.security.authorization", "false")
                    .config("spark.hadoop.fs.defaultFS", "file:///")
                    .config("spark.sql.warehouse.dir", "/tmp/spark-warehouse")
                    // Java 17 compatibility options
                    .config("spark.driver.extraJavaOptions", jvmOptions)
                    .config("spark.executor.extraJavaOptions", jvmOptions)
                    // Kryo serialization configuration with registration disabled
                    .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                    .config("spark.kryo.registrationRequired", "false")
                    .config("spark.kryo.unsafe", "true")
                    .config("spark.kryo.referenceTracking", "false")
                    .config("spark.kryo.recordTracking", "false")
                    .getOrCreate();
            
            // Create and execute the transformation job
            CSVTransformJob transformJob = new CSVTransformJob();
            boolean success = transformJob.processFile(spark, inputPath, outputPath, dslScript, jobId);
            
            if (success) {
                System.out.println("Transformation completed successfully");
                System.exit(0);
            } else {
                System.err.println("Transformation failed");
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("Error in transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (spark != null) {
                spark.stop();
            }
        }
    }
}
