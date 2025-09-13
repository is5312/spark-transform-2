package com.sparktransform.sparkjob;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import javax.sql.DataSource;

/**
 * Spring Configuration for partition-level context
 */
@Configuration
@ComponentScan("com.ssc.isvc")
@EnableAutoConfiguration(exclude = {
    WebMvcAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class PartitionSpringConfiguration {
    
    /**
     * Configure JDBC DataSource if not already present
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource() {
        // Configure your database connection here
        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        
        // Example configuration - replace with your actual database settings
        builder.driverClassName("org.postgresql.Driver");
        builder.url("jdbc:postgresql://localhost:5432/your_database");
        builder.username("your_username");
        builder.password("your_password");
        
        System.out.println("🔌 PartitionSpringConfiguration: DataSource configured");
        return builder.build();
    }
    
    /**
     * Configure Redis Template if not already present
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        
        // Configure Redis connection factory
        JedisConnectionFactory factory = new JedisConnectionFactory();
        factory.setHostName("localhost");
        factory.setPort(6379);
        factory.afterPropertiesSet();
        
        template.setConnectionFactory(factory);
        template.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());
        
        System.out.println("🔌 PartitionSpringConfiguration: RedisTemplate configured");
        return template;
    }
}
