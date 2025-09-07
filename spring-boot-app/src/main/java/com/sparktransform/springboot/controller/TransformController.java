package com.sparktransform.springboot.controller;

import com.sparktransform.springboot.dto.TransformRequest;
import com.sparktransform.springboot.dto.TransformResponse;
import com.sparktransform.springboot.service.SparkJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main controller for Spark transformation jobs
 */
@RestController
@RequestMapping("/api/transform")
@CrossOrigin(origins = "*")
public class TransformController {

    @Autowired
    private SparkJobService sparkJobService;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Spark Transform API",
                "message", "Service is running with Spark integration"
        ));
    }

    /**
     * Test endpoint
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        return ResponseEntity.ok(Map.of(
                "message", "API is working!",
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));
    }

    /**
     * Submit a CSV transformation job
     */
    @PostMapping("/submit")
    public ResponseEntity<TransformResponse> submitJob(@Valid @RequestBody TransformRequest request) {
        try {
            TransformResponse response = sparkJobService.submitJobSync(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            TransformResponse errorResponse = new TransformResponse(
                    request.getJobId(),
                    "ERROR",
                    "Error submitting job: " + e.getMessage(),
                    null,
                    System.currentTimeMillis()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Submit a CSV transformation job asynchronously
     */
    @PostMapping("/submit-async")
    public ResponseEntity<Map<String, String>> submitJobAsync(@Valid @RequestBody TransformRequest request) {
        try {
            CompletableFuture<TransformResponse> future = sparkJobService.submitJob(request);

            // Return immediately with job ID
            return ResponseEntity.ok(Map.of(
                    "job_id", request.getJobId(),
                    "status", "SUBMITTED",
                    "message", "Job submitted successfully",
                    "timestamp", String.valueOf(System.currentTimeMillis())
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "job_id", request.getJobId(),
                    "status", "ERROR",
                    "message", "Error submitting job: " + e.getMessage(),
                    "timestamp", String.valueOf(System.currentTimeMillis())
            ));
        }
    }
}