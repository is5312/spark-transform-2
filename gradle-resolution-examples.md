# Gradle Resolution Strategy Examples

## 1. Combined Approach (Recommended)
```gradle
configurations.all {
    resolutionStrategy {
        // Force declarations - simple and direct
        force 'com.esotericsoftware:kryo:4.0.3'
        force 'org.slf4j:slf4j-api:1.7.36'
        
        // eachDependency - more flexible and conditional
        eachDependency { DependencyResolveDetails details ->
            if (details.requested.group == 'com.esotericsoftware' && details.requested.name == 'kryo') {
                details.useVersion '4.0.3'
                details.because 'Force Kryo 4.x for Spark compatibility'
            }
        }
    }
}
```

## 2. Multiple resolutionStrategy Blocks (NOT Recommended)
```gradle
// This creates multiple resolution strategies - can be confusing
configurations.all {
    resolutionStrategy {
        force 'com.esotericsoftware:kryo:4.0.3'
    }
    
    resolutionStrategy {
        eachDependency { DependencyResolveDetails details ->
            // This could override the force above
        }
    }
}
```

## 3. eachDependency Only Approach
```gradle
configurations.all {
    resolutionStrategy {
        eachDependency { DependencyResolveDetails details ->
            // Force Kryo 4.x
            if (details.requested.group == 'com.esotericsoftware' && details.requested.name == 'kryo') {
                details.useVersion '4.0.3'
                details.because 'Force Kryo 4.x for Spark compatibility'
            }
            
            // Force SLF4J version
            if (details.requested.group == 'org.slf4j' && details.requested.name == 'slf4j-api') {
                details.useVersion '1.7.36'
                details.because 'Consistent SLF4J version'
            }
            
            // Conditional version forcing
            if (details.requested.group.startsWith('org.springframework')) {
                if (details.requested.name.contains('spring-core')) {
                    details.useVersion '6.0.13'
                    details.because 'Spring 6.0.x compatibility'
                }
            }
        }
    }
}
```

## 4. Project-Specific Resolution Strategy
```gradle
// In a specific subproject's build.gradle
configurations.all {
    resolutionStrategy {
        // Inherit global forces from root project
        
        // Add project-specific rules
        eachDependency { DependencyResolveDetails details ->
            // Project-specific overrides
            if (project.name == 'spark-job') {
                if (details.requested.group == 'org.apache.spark') {
                    details.useVersion '3.5.2'
                    details.because 'Specific Spark version for job module'
                }
            }
        }
    }
}
```

## Key Points:

1. **Single resolutionStrategy block**: Combine `force` and `eachDependency` in one block
2. **force vs eachDependency**: 
   - `force` is simpler for direct version forcing
   - `eachDependency` provides more control and conditional logic
3. **Order matters**: Later rules can override earlier ones
4. **Global vs Local**: Global rules in root build.gradle apply to all subprojects
5. **Performance**: `force` is slightly faster than `eachDependency` for simple cases
