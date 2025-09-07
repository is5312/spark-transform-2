package com.sparktransform.springboot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for CSV transformation jobs
 */
public class TransformResponse {
    
    @JsonProperty("job_id")
    private String jobId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("spark_app_id")
    private String sparkAppId;
    
    @JsonProperty("submission_time")
    private long submissionTime;
    
    // Default constructor
    public TransformResponse() {}
    
    // Constructor with parameters
    public TransformResponse(String jobId, String status, String message, String sparkAppId, long submissionTime) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
        this.sparkAppId = sparkAppId;
        this.submissionTime = submissionTime;
    }
    
    // Getters and setters
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getSparkAppId() {
        return sparkAppId;
    }
    
    public void setSparkAppId(String sparkAppId) {
        this.sparkAppId = sparkAppId;
    }
    
    public long getSubmissionTime() {
        return submissionTime;
    }
    
    public void setSubmissionTime(long submissionTime) {
        this.submissionTime = submissionTime;
    }
    
    @Override
    public String toString() {
        return "TransformResponse{" +
                "jobId='" + jobId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", sparkAppId='" + sparkAppId + '\'' +
                ", submissionTime=" + submissionTime +
                '}';
    }
}
