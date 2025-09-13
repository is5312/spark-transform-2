package com.ssc.isvc.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example service class that demonstrates Spring dependency injection
 * within Spark partition processing for data enrichment operations
 */
@Service
public class DataEnrichmentService {
    
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired(required = false)
    private DataSource dataSource;
    
    /**
     * Enrich data using database lookup
     */
    public String enrichFromDatabase(String key) {
        if (jdbcTemplate != null) {
            try {
                return jdbcTemplate.queryForObject(
                    "SELECT enriched_value FROM enrichment_table WHERE key = ?", 
                    String.class, 
                    key
                );
            } catch (Exception e) {
                System.err.println("Database enrichment failed for key '" + key + "': " + e.getMessage());
            }
        }
        return key; // Return original if enrichment fails
    }
    
    /**
     * Enrich data using Redis cache
     */
    public String enrichFromRedis(String key) {
        if (redisTemplate != null) {
            try {
                Object value = redisTemplate.opsForValue().get("enrich:" + key);
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception e) {
                System.err.println("Redis enrichment failed for key '" + key + "': " + e.getMessage());
            }
        }
        return key; // Return original if enrichment fails
    }
    
    /**
     * Enrich data using in-memory cache with fallback to external sources
     */
    public String enrichWithFallback(String key) {
        // Check in-memory cache first
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        
        // Try Redis next
        String redisValue = enrichFromRedis(key);
        if (!redisValue.equals(key)) {
            cache.put(key, redisValue);
            return redisValue;
        }
        
        // Fallback to database
        String dbValue = enrichFromDatabase(key);
        if (!dbValue.equals(key)) {
            cache.put(key, dbValue);
            // Cache in Redis for next time
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set("enrich:" + key, dbValue);
                } catch (Exception e) {
                    System.err.println("Failed to cache in Redis: " + e.getMessage());
                }
            }
        }
        
        return dbValue;
    }
    
    /**
     * Get connection status for monitoring
     */
    public String getConnectionStatus() {
        StringBuilder status = new StringBuilder();
        
        status.append("Database: ").append(dataSource != null ? "Connected" : "Not available");
        status.append(", Redis: ").append(redisTemplate != null ? "Connected" : "Not available");
        status.append(", Cache entries: ").append(cache.size());
        
        return status.toString();
    }
}
