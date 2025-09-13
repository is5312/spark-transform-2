package com.sparktransform.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Custom DSL execution engine for data transformation with performance optimizations
 * Now supports Spring context integration for accessing beans, databases, Redis, and gRPC services
 */
public class DSLExecutor implements java.io.Serializable, SpringAwareDSLExecutor {
    
    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> parsedDSLCache; // Cache parsed DSL scripts
    private final Map<String, List<TransformationRule>> compiledRulesCache; // Cache compiled rules
    
    // Spring context for accessing beans, services, databases, etc.
    private transient org.springframework.context.ApplicationContext springContext;
    
    public DSLExecutor() {
        this.objectMapper = new ObjectMapper();
        // Pre-size maps for better performance with large datasets
        this.parsedDSLCache = new HashMap<>(16);
        this.compiledRulesCache = new HashMap<>(16);
    }
    
    /**
     * Execute DSL transformation on a single row of data with caching optimizations
     * @param inputRow The input data as a map of column names to values
     * @param dslScript The DSL script to execute
     * @return Transformed data as a map
     */
    public Map<String, Object> executeTransformation(Map<String, Object> inputRow, String dslScript) {
        try {
            // Get compiled rules from cache or compile them
            List<TransformationRule> rules = getCompiledRules(dslScript);
            
            Map<String, Object> result = new HashMap<>();
            
            // Process each transformation rule
            for (TransformationRule rule : rules) {
                Object transformedValue = executeOperationOptimized(inputRow, rule);
                result.put(rule.targetColumn, transformedValue);
            }
            
            return result;
            
        } catch (Exception e) {
            throw new RuntimeException("Error executing DSL transformation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get compiled transformation rules from cache or compile them
     */
    private List<TransformationRule> getCompiledRules(String dslScript) {
        // Check cache first
        if (compiledRulesCache.containsKey(dslScript)) {
            return compiledRulesCache.get(dslScript);
        }
        
        // Parse and compile rules
        JsonNode dslNode = getParsedDSL(dslScript);
        List<TransformationRule> rules = new ArrayList<>();
        
        if (dslNode.has("transformations")) {
            JsonNode transformations = dslNode.get("transformations");
            
            for (JsonNode transformation : transformations) {
                String targetColumn = transformation.get("target").asText();
                String operation = transformation.has("operation") ? 
                    transformation.get("operation").asText() : 
                    transformation.get("type").asText();
                
                rules.add(new TransformationRule(targetColumn, operation, transformation));
            }
        }
        
        // Cache the compiled rules
        compiledRulesCache.put(dslScript, rules);
        return rules;
    }
    
    /**
     * Get parsed DSL from cache or parse it
     */
    private JsonNode getParsedDSL(String dslScript) {
        if (parsedDSLCache.containsKey(dslScript)) {
            return parsedDSLCache.get(dslScript);
        }
        
        try {
            JsonNode dslNode = objectMapper.readTree(dslScript);
            parsedDSLCache.put(dslScript, dslNode);
            return dslNode;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing DSL script: " + e.getMessage(), e);
        }
    }
    
    /**
     * Optimized operation execution using pre-compiled rules
     */
    private Object executeOperationOptimized(Map<String, Object> inputRow, TransformationRule rule) {
        switch (rule.operation.toLowerCase()) {
            case "copy":
                return executeCopy(inputRow, rule.transformation);
            case "concat":
                return executeConcat(inputRow, rule.transformation);
            case "uppercase":
                return executeUppercase(inputRow, rule.transformation);
            case "lowercase":
                return executeLowercase(inputRow, rule.transformation);
            case "add":
                return executeAdd(inputRow, rule.transformation);
            case "multiply":
                return executeMultiply(inputRow, rule.transformation);
            case "conditional":
                return executeConditional(inputRow, rule.transformation);
            case "constant":
                return executeConstant(rule.transformation);
            case "enrich_db":
                return executeEnrichDatabase(inputRow, rule.transformation);
            case "enrich_redis":
                return executeEnrichRedis(inputRow, rule.transformation);
            case "enrich_grpc":
                return executeEnrichGrpc(inputRow, rule.transformation);
            case "validate_grpc":
                return executeValidateGrpc(inputRow, rule.transformation);
            case "process_autoconfig":
                return executeAutoConfigProcess(inputRow, rule.transformation);
            case "cache_lookup":
                return executeCacheLookup(inputRow, rule.transformation);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + rule.operation);
        }
    }
    
    
    private Object executeCopy(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        return inputRow.get(sourceColumn);
    }
    
    private Object executeConcat(Map<String, Object> inputRow, JsonNode transformation) {
        StringBuilder result = new StringBuilder();
        JsonNode sources = transformation.get("sources");
        
        for (JsonNode source : sources) {
            String sourceValue = source.asText();
            
            // Check if this is a column name (exists in inputRow) or a literal string
            if (inputRow.containsKey(sourceValue)) {
                // It's a column name, get the value
                Object value = inputRow.get(sourceValue);
                if (value != null) {
                    result.append(value.toString());
                }
            } else {
                // It's a literal string (like " " for space)
                result.append(sourceValue);
            }
        }
        
        return result.toString();
    }
    
    private Object executeUppercase(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        Object value = inputRow.get(sourceColumn);
        return value != null ? value.toString().toUpperCase() : null;
    }
    
    private Object executeLowercase(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        Object value = inputRow.get(sourceColumn);
        return value != null ? value.toString().toLowerCase() : null;
    }
    
    private Object executeAdd(Map<String, Object> inputRow, JsonNode transformation) {
        JsonNode sources = transformation.get("sources");
        double sum = 0.0;
        
        for (JsonNode source : sources) {
            if (source.isNumber()) {
                sum += source.asDouble();
            } else {
                String columnName = source.asText();
                Object value = inputRow.get(columnName);
                if (value != null) {
                    try {
                        sum += Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }
        }
        
        // Return as Integer if the result is a whole number, otherwise as Double
        if (sum == Math.floor(sum)) {
            return (int) sum;
        } else {
            return sum;
        }
    }
    
    private Object executeMultiply(Map<String, Object> inputRow, JsonNode transformation) {
        JsonNode sources = transformation.get("sources");
        double product = 1.0;
        
        for (JsonNode source : sources) {
            if (source.isNumber()) {
                product *= source.asDouble();
            } else {
                String columnName = source.asText();
                Object value = inputRow.get(columnName);
                if (value != null) {
                    try {
                        product *= Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }
        }
        
        // Return as Integer if the result is a whole number, otherwise as Double
        if (product == Math.floor(product)) {
            return (int) product;
        } else {
            return product;
        }
    }
    
    private Object executeConditional(Map<String, Object> inputRow, JsonNode transformation) {
        String condition = transformation.get("condition").asText();
        String sourceColumn = transformation.get("source").asText();
        Object value = inputRow.get(sourceColumn);
        
        // Simple condition evaluation (can be extended)
        if ("not_null".equals(condition)) {
            return value != null;
        } else if ("equals".equals(condition)) {
            String expectedValue = transformation.get("expected").asText();
            return expectedValue.equals(value != null ? value.toString() : null);
        }
        
        return false;
    }
    
    private Object executeConstant(JsonNode transformation) {
        return transformation.get("value").asText();
    }
    
    /**
     * Validate DSL script syntax
     */
    public boolean validateDSL(String dslScript) {
        try {
            JsonNode dslNode = objectMapper.readTree(dslScript);
            return dslNode.has("transformations");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Set the Spring Application Context for accessing beans and services
     */
    @Override
    public void setSpringContext(org.springframework.context.ApplicationContext springContext) {
        this.springContext = springContext;
    }
    
    /**
     * Get the Spring Application Context
     */
    @Override
    public org.springframework.context.ApplicationContext getSpringContext() {
        return this.springContext;
    }
    
    /**
     * Get a Spring bean by type - utility method for transformations
     */
    public <T> T getSpringBean(Class<T> beanType) {
        if (springContext != null) {
            try {
                return springContext.getBean(beanType);
            } catch (Exception e) {
                System.err.println("Failed to get Spring bean of type " + beanType.getSimpleName() + ": " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Get a Spring bean by name - utility method for transformations
     */
    @SuppressWarnings("unchecked")
    public <T> T getSpringBean(String beanName, Class<T> expectedType) {
        if (springContext != null) {
            try {
                Object bean = springContext.getBean(beanName);
                if (expectedType.isInstance(bean)) {
                    return (T) bean;
                }
            } catch (Exception e) {
                System.err.println("Failed to get Spring bean '" + beanName + "': " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Execute database enrichment operation using Spring JDBC
     */
    private Object executeEnrichDatabase(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        Object sourceValue = inputRow.get(sourceColumn);
        
        if (sourceValue == null) {
            return null;
        }
        
        try {
            // Get DataEnrichmentService from Spring context
            Object enrichmentService = getSpringBean("dataEnrichmentService", Object.class);
            if (enrichmentService != null) {
                // Use reflection to call enrichFromDatabase method
                java.lang.reflect.Method method = enrichmentService.getClass()
                    .getMethod("enrichFromDatabase", String.class);
                return method.invoke(enrichmentService, sourceValue.toString());
            }
        } catch (Exception e) {
            System.err.println("Database enrichment failed: " + e.getMessage());
        }
        
        return sourceValue; // Return original value if enrichment fails
    }
    
    /**
     * Execute Redis enrichment operation
     */
    private Object executeEnrichRedis(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        Object sourceValue = inputRow.get(sourceColumn);
        
        if (sourceValue == null) {
            return null;
        }
        
        try {
            // Get DataEnrichmentService from Spring context
            Object enrichmentService = getSpringBean("dataEnrichmentService", Object.class);
            if (enrichmentService != null) {
                // Use reflection to call enrichFromRedis method
                java.lang.reflect.Method method = enrichmentService.getClass()
                    .getMethod("enrichFromRedis", String.class);
                return method.invoke(enrichmentService, sourceValue.toString());
            }
        } catch (Exception e) {
            System.err.println("Redis enrichment failed: " + e.getMessage());
        }
        
        return sourceValue; // Return original value if enrichment fails
    }
    
    /**
     * Execute gRPC enrichment operation
     */
    private Object executeEnrichGrpc(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        Object sourceValue = inputRow.get(sourceColumn);
        
        if (sourceValue == null) {
            return null;
        }
        
        try {
            // Get GrpcDataProcessor from Spring context
            Object grpcProcessor = getSpringBean("grpcDataProcessor", Object.class);
            if (grpcProcessor != null) {
                // Use reflection to call enrichViaGrpc method
                java.lang.reflect.Method method = grpcProcessor.getClass()
                    .getMethod("enrichViaGrpc", String.class);
                return method.invoke(grpcProcessor, sourceValue.toString());
            }
        } catch (Exception e) {
            System.err.println("gRPC enrichment failed: " + e.getMessage());
        }
        
        return sourceValue; // Return original value if enrichment fails
    }
    
    /**
     * Execute gRPC validation operation
     */
    private Object executeValidateGrpc(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        Object sourceValue = inputRow.get(sourceColumn);
        
        if (sourceValue == null) {
            return false;
        }
        
        try {
            // Get GrpcDataProcessor from Spring context
            Object grpcProcessor = getSpringBean("grpcDataProcessor", Object.class);
            if (grpcProcessor != null) {
                // Use reflection to call validateViaGrpc method
                java.lang.reflect.Method method = grpcProcessor.getClass()
                    .getMethod("validateViaGrpc", String.class);
                return method.invoke(grpcProcessor, sourceValue.toString());
            }
        } catch (Exception e) {
            System.err.println("gRPC validation failed: " + e.getMessage());
        }
        
        return false; // Return false if validation fails
    }
    
    /**
     * Execute auto-configured processor operation
     */
    private Object executeAutoConfigProcess(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        Object sourceValue = inputRow.get(sourceColumn);
        
        if (sourceValue == null) {
            return null;
        }
        
        try {
            // Get auto-configured PartitionDataProcessor from Spring context
            Object dataProcessor = getSpringBean("partitionDataProcessor", Object.class);
            if (dataProcessor != null) {
                // Use reflection to call processData method
                java.lang.reflect.Method method = dataProcessor.getClass()
                    .getMethod("processData", String.class);
                return method.invoke(dataProcessor, sourceValue.toString());
            }
        } catch (Exception e) {
            System.err.println("AutoConfig processing failed: " + e.getMessage());
        }
        
        return sourceValue; // Return original value if processing fails
    }
    
    /**
     * Execute cache lookup operation using auto-configured cache manager
     */
    private Object executeCacheLookup(Map<String, Object> inputRow, JsonNode transformation) {
        String sourceColumn = transformation.get("source").asText();
        Object sourceValue = inputRow.get(sourceColumn);
        
        if (sourceValue == null) {
            return null;
        }
        
        try {
            // Get auto-configured PartitionCacheManager from Spring context
            Object cacheManager = getSpringBean("partitionCacheManager", Object.class);
            if (cacheManager != null) {
                // Use reflection to call get method
                java.lang.reflect.Method getMethod = cacheManager.getClass()
                    .getMethod("get", String.class);
                Object cachedValue = getMethod.invoke(cacheManager, sourceValue.toString());
                
                if (cachedValue != null) {
                    return cachedValue;
                } else {
                    // If not in cache, process and cache the result
                    String processedValue = "CACHED_" + sourceValue.toString();
                    java.lang.reflect.Method putMethod = cacheManager.getClass()
                        .getMethod("put", String.class, Object.class);
                    putMethod.invoke(cacheManager, sourceValue.toString(), processedValue);
                    return processedValue;
                }
            }
        } catch (Exception e) {
            System.err.println("Cache lookup failed: " + e.getMessage());
        }
        
        return sourceValue; // Return original value if cache lookup fails
    }
    
    /**
     * Inner class to represent a compiled transformation rule
     */
    private static class TransformationRule implements java.io.Serializable {
        final String targetColumn;
        final String operation;
        final JsonNode transformation;
        
        TransformationRule(String targetColumn, String operation, JsonNode transformation) {
            this.targetColumn = targetColumn;
            this.operation = operation;
            this.transformation = transformation;
        }
    }
}

