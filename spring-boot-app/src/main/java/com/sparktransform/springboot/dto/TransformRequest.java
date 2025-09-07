package com.sparktransform.springboot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for CSV transformation jobs
 */
public class TransformRequest {
    
    @NotBlank(message = "Input path is required")
    @JsonProperty("input_path")
    private String inputPath;
    
    @NotBlank(message = "Output path is required")
    @JsonProperty("output_path")
    private String outputPath;
    
    @NotBlank(message = "DSL script is required")
    @JsonProperty("dsl_script")
    private String dslScript;
    
    @NotBlank(message = "Job ID is required")
    @JsonProperty("job_id")
    private String jobId;
    
    // Default constructor
    public TransformRequest() {}
    
    // Constructor with parameters
    public TransformRequest(String inputPath, String outputPath, String dslScript, String jobId) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.dslScript = dslScript;
        this.jobId = jobId;
    }
    
    // Getters and setters
    public String getInputPath() {
        return inputPath;
    }
    
    public void setInputPath(String inputPath) {
        this.inputPath = inputPath;
    }
    
    public String getOutputPath() {
        return outputPath;
    }
    
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }
    
    public String getDslScript() {
        return dslScript;
    }
    
    public void setDslScript(String dslScript) {
        this.dslScript = dslScript;
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    @Override
    public String toString() {
        return "TransformRequest{" +
                "inputPath='" + inputPath + '\'' +
                ", outputPath='" + outputPath + '\'' +
                ", dslScript='" + dslScript + '\'' +
                ", jobId='" + jobId + '\'' +
                '}';
    }
}
