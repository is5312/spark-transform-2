package com.sparktransform.sparkjob;

import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;

/**
 * AutoConfiguration Enabler Configuration
 */
@Configuration
@EnableAutoConfiguration
@Import({
    DataSourceAutoConfiguration.class,
    JdbcTemplateAutoConfiguration.class,
    RedisAutoConfiguration.class,
    TransactionAutoConfiguration.class,
    JacksonAutoConfiguration.class,
    ValidationAutoConfiguration.class
})
public class AutoConfigurationEnablerConfiguration {
    
    /**
     * Configure AutoConfiguration exclusions for Spark environment
     */
    @Bean
    public AutoConfigurationExcludeFilter autoConfigurationExcludeFilter() {
        return new AutoConfigurationExcludeFilter() {
            @Override
            public boolean match(MetadataReader metadataReader,
                               MetadataReaderFactory metadataReaderFactory) throws IOException {
                
                String className = metadataReader.getClassMetadata().getClassName();
                
                // Exclude web-related AutoConfigurations that are not suitable for Spark partitions
                boolean shouldExclude = className.contains("Web") ||
                       className.contains("Servlet") ||
                       className.contains("Mvc") ||
                       className.contains("Security") ||
                       className.contains("Actuator") ||
                       className.contains("Management") ||
                       className.contains("Jmx");
                
                if (shouldExclude) {
                    System.out.println("🚫 AutoConfigurationEnablerConfiguration: Excluded " + className);
                }
                
                return shouldExclude;
            }
        };
    }
}
