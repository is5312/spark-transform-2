package com.sparktransform.springboot.service;

import com.sparktransform.springboot.dto.TransformRequest;
import com.sparktransform.springboot.dto.TransformResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.HashMap;

/**
 * Service for submitting Spark jobs using Spark REST API
 */
@Service
public class SparkJobService {
    
    private static final Logger logger = LoggerFactory.getLogger(SparkJobService.class);
    
    @Value("${spark.master.rest.url:http://spark-master:6066}")
    private String sparkMasterRestUrl;
    
    @Value("${spark.app.name:SparkTransformAPI}")
    private String appName;
    
    @Value("${spark.driver.memory:2g}")
    private String driverMemory;
    
    @Value("${spark.executor.memory:2g}")
    private String executorMemory;
    
    @Value("${spark.executor.cores:2}")
    private String executorCores;
    
    @Value("${spark.executor.instances:3}")
    private String executorInstances;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing Spark REST API client connecting to: {}", sparkMasterRestUrl);
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            executorService.shutdown();
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
    
    /**
     * Submit a Spark job asynchronously via REST API
     */
    public CompletableFuture<TransformResponse> submitJob(TransformRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Submitting Spark job via REST API: {}", request.getJobId());
                
                // Submit job via Spark REST API
                String submissionId = submitJobViaRestApi(request);
                
                if (submissionId != null) {
                    logger.info("Spark job submitted successfully: {} with submission ID: {}", 
                            request.getJobId(), submissionId);
                    return new TransformResponse(
                            request.getJobId(),
                            "SUBMITTED",
                            "Job submitted successfully",
                            submissionId,
                            System.currentTimeMillis()
                    );
                } else {
                    logger.error("Failed to submit Spark job: {}", request.getJobId());
                    return new TransformResponse(
                            request.getJobId(),
                            "FAILED",
                            "Failed to submit job",
                            null,
                            System.currentTimeMillis()
                    );
                }
                
            } catch (Exception e) {
                logger.error("Error in job submission: {}", request.getJobId(), e);
                return new TransformResponse(
                        request.getJobId(),
                        "ERROR",
                        "Error in job submission: " + e.getMessage(),
                        null,
                        System.currentTimeMillis()
                );
            }
        }, executorService);
    }
    
    /**
     * Submit a Spark job synchronously
     */
    public TransformResponse submitJobSync(TransformRequest request) {
        try {
            return submitJob(request).get();
        } catch (Exception e) {
            logger.error("Error in synchronous job submission: {}", request.getJobId(), e);
            return new TransformResponse(
                    request.getJobId(),
                    "ERROR",
                    "Error in job submission: " + e.getMessage(),
                    null,
                    System.currentTimeMillis()
            );
        }
    }
    
    /**
     * Submit job via Spark REST API
     */
    private String submitJobViaRestApi(TransformRequest request) {
        try {
            // Prepare the submission request according to Spark REST API specification
            Map<String, Object> submissionRequest = new HashMap<>();
            submissionRequest.put("action", "CreateSubmissionRequest");
            submissionRequest.put("clientSparkVersion", "3.5.0");
            submissionRequest.put("appResource", "/opt/spark/jars/spark-job.jar");
            submissionRequest.put("mainClass", "com.sparktransform.sparkjob.SparkTransformDriver");
            submissionRequest.put("appArgs", new String[]{
                request.getInputPath(),
                request.getOutputPath(),
                request.getDslScript(),
                request.getJobId()
            });
            
            // Set environment variables
            Map<String, String> environmentVariables = new HashMap<>();
            environmentVariables.put("SPARK_ENV_LOADED", "1");
            submissionRequest.put("environmentVariables", environmentVariables);
            
            // Set Spark properties according to official specification
            Map<String, String> sparkProperties = new HashMap<>();
            sparkProperties.put("spark.master", "spark://spark-master:7077");
            sparkProperties.put("spark.app.name", appName + "-" + request.getJobId());
            sparkProperties.put("spark.driver.memory", driverMemory);
            sparkProperties.put("spark.driver.cores", "1");
            sparkProperties.put("spark.executor.memory", executorMemory);
            sparkProperties.put("spark.executor.cores", executorCores);
            sparkProperties.put("spark.executor.instances", executorInstances);
            
            // Serialization optimizations
            sparkProperties.put("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
            sparkProperties.put("spark.kryo.registrationRequired", "false");
            sparkProperties.put("spark.kryo.unsafe", "true");
            sparkProperties.put("spark.kryo.referenceTracking", "false");
            sparkProperties.put("spark.kryo.recordTracking", "false");
            
            // Performance optimizations for large files
            sparkProperties.put("spark.sql.adaptive.enabled", "true");
            sparkProperties.put("spark.sql.adaptive.coalescePartitions.enabled", "true");
            sparkProperties.put("spark.sql.adaptive.coalescePartitions.minPartitionNum", "1");
            sparkProperties.put("spark.sql.adaptive.coalescePartitions.initialPartitionNum", "200");
            sparkProperties.put("spark.sql.adaptive.skewJoin.enabled", "true");
            sparkProperties.put("spark.sql.adaptive.localShuffleReader.enabled", "true");
            
            // Additional optimizations for large file processing
            sparkProperties.put("spark.sql.files.maxPartitionBytes", "134217728"); // 128MB per partition
            sparkProperties.put("spark.sql.files.openCostInBytes", "4194304"); // 4MB open cost
            sparkProperties.put("spark.sql.broadcastTimeout", "36000"); // 10 minutes for large broadcasts
            sparkProperties.put("spark.rdd.compress", "true"); // Compress RDD storage
            sparkProperties.put("spark.io.compression.codec", "lz4"); // Fast compression for shuffles
            
            // Memory and caching optimizations
            sparkProperties.put("spark.sql.execution.arrow.pyspark.enabled", "false");
            sparkProperties.put("spark.sql.parquet.compression.codec", "snappy");
            sparkProperties.put("spark.sql.parquet.enableVectorizedReader", "true");
            sparkProperties.put("spark.sql.parquet.mergeSchema", "false");
            
            // Network and shuffle optimizations
            sparkProperties.put("spark.network.timeout", "800s");
            sparkProperties.put("spark.sql.shuffle.partitions", "200");
            sparkProperties.put("spark.sql.adaptive.shuffle.targetPostShuffleInputSize", "64MB");
            sparkProperties.put("spark.sql.adaptive.advisoryPartitionSizeInBytes", "64MB");
            
            // Dynamic allocation for better resource utilization
            sparkProperties.put("spark.dynamicAllocation.enabled", "true");
            sparkProperties.put("spark.dynamicAllocation.minExecutors", "1");
            sparkProperties.put("spark.dynamicAllocation.maxExecutors", "10");
            sparkProperties.put("spark.dynamicAllocation.initialExecutors", "2");
            
            // Garbage collection optimizations
            sparkProperties.put("spark.executor.extraJavaOptions", 
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
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
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
                "--add-opens=java.base/sun.security.action=ALL-UNNAMED " +
                "--add-opens=java.base/sun.util=ALL-UNNAMED " +
                "-XX:+UseG1GC " +
                "-XX:+UnlockExperimentalVMOptions " +
                "-XX:+UseCGroupMemoryLimitForHeap " +
                "-XX:MaxGCPauseMillis=200 " +
                "-XX:+PrintGCDetails " +
                "-XX:+PrintGCTimeStamps");
            
                    // Add JVM options to bypass Java 17 module system restrictions for driver
                    sparkProperties.put("spark.driver.extraJavaOptions",
                        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
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
                        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
                        "--add-opens=java.base/sun.security.action=ALL-UNNAMED " +
                        "--add-opens=java.base/sun.util=ALL-UNNAMED " +
                        "-Dhadoop.security.authentication=simple " +
                        "-Dhadoop.security.authorization=false " +
                        "-Djava.security.krb5.conf=/dev/null " +
                        "-XX:+UseG1GC " +
                        "-XX:MaxGCPauseMillis=200");
            
            submissionRequest.put("sparkProperties", sparkProperties);
            
            // Submit to Spark REST API
            String url = sparkMasterRestUrl + "/v1/submissions/create";
            logger.info("Submitting to Spark REST API: {}", url);
            logger.info("Request payload: {}", submissionRequest);
            
            Map<String, Object> response = restTemplate.postForObject(url, submissionRequest, Map.class);
            
            if (response != null && response.containsKey("submissionId")) {
                return (String) response.get("submissionId");
            } else {
                logger.error("Invalid response from Spark REST API: {}", response);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error submitting job via REST API", e);
            return null;
        }
    }
}