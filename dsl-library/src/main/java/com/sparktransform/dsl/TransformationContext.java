package com.sparktransform.dsl;

import java.util.Map;
import java.util.HashMap;

/**
 * Context for holding transformation metadata and configuration
 */
public class TransformationContext {
    
    private final Map<String, Object> metadata;
    private final String jobId;
    private final long timestamp;
    
    public TransformationContext(String jobId) {
        this.jobId = jobId;
        this.timestamp = System.currentTimeMillis();
        this.metadata = new HashMap<>();
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public Map<String, Object> getAllMetadata() {
        return new HashMap<>(metadata);
    }
    
    public void addMetadata(Map<String, Object> additionalMetadata) {
        metadata.putAll(additionalMetadata);
    }
}

