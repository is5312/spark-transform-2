package com.sparktransform.dsl;

import org.springframework.context.ApplicationContext;

/**
 * Interface for DSL executors that can be enhanced with Spring context
 * for accessing Spring-managed beans, services, and database connections
 */
public interface SpringAwareDSLExecutor {
    
    /**
     * Set the Spring Application Context for this DSL executor
     * @param springContext The Spring application context
     */
    void setSpringContext(ApplicationContext springContext);
    
    /**
     * Get the Spring Application Context
     * @return The Spring application context, or null if not available
     */
    ApplicationContext getSpringContext();
    
    /**
     * Check if Spring context is available
     * @return true if Spring context is available, false otherwise
     */
    default boolean isSpringContextAvailable() {
        return getSpringContext() != null;
    }
}
