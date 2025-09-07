package com.sparktransform.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.sparktransform.springboot")
public class SparkTransformApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SparkTransformApplication.class, args);
    }
}

