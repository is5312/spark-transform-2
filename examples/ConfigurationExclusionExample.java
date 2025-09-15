package examples;

import com.sparktransform.sparkjob.ConfigurationExclusionManager;
import com.sparktransform.sparkjob.SpringContextManager;

/**
 * Example demonstrating how to programmatically exclude Spring @Configuration 
 * classes from libraries without modifying the library code itself.
 * 
 * This example shows various approaches to exclude configurations:
 * 1. Using the centralized ConfigurationExclusionManager
 * 2. Runtime addition/removal of exclusions
 * 3. Integration with SpringContextManager
 */
public class ConfigurationExclusionExample {
    
    public static void main(String[] args) {
        System.out.println("=== Spring Configuration Exclusion Example ===\n");
        
        // 1. Static exclusions (already configured in ConfigurationExclusionManager)
        demonstrateStaticExclusions();
        
        // 2. Runtime dynamic exclusions
        demonstrateDynamicExclusions();
        
        // 3. Integration with Spring Context
        demonstrateSpringContextIntegration();
        
        // 4. Debugging and monitoring exclusions
        demonstrateDebuggingFeatures();
    }
    
    private static void demonstrateStaticExclusions() {
        System.out.println("1. STATIC EXCLUSIONS (Pre-configured)");
        System.out.println("--------------------------------------");
        
        // These configurations are already excluded via static configuration
        String[] testConfigurations = {
            "com.some.library.UnwantedConfiguration",        // Exact match
            "com.problematic.library.SomeConfig",           // Package match
            "com.library.SecurityConfiguration",            // Pattern match
            "com.allowed.library.GoodConfiguration"         // Should NOT be excluded
        };
        
        for (String config : testConfigurations) {
            boolean excluded = ConfigurationExclusionManager.isConfigurationExcluded(config);
            System.out.println("  " + config + " -> " + 
                (excluded ? "🚫 EXCLUDED" : "✅ ALLOWED"));
        }
        System.out.println();
    }
    
    private static void demonstrateDynamicExclusions() {
        System.out.println("2. DYNAMIC EXCLUSIONS (Runtime)");
        System.out.println("--------------------------------");
        
        // Add new exclusions at runtime
        System.out.println("Adding runtime exclusions...");
        ConfigurationExclusionManager.addExcludedConfiguration("com.runtime.library.BadConfig");
        ConfigurationExclusionManager.addExcludedPackage("com.newlibrary.problematic");
        ConfigurationExclusionManager.addExcludedPattern("TestConfig");
        
        // Test the newly added exclusions
        String[] runtimeTests = {
            "com.runtime.library.BadConfig",                 // Newly added exact match
            "com.newlibrary.problematic.AnyConfig",         // Newly added package
            "com.anywhere.MyTestConfig",                     // Newly added pattern
            "com.safe.library.GoodConfig"                   // Should NOT be excluded
        };
        
        System.out.println("Testing runtime exclusions:");
        for (String config : runtimeTests) {
            boolean excluded = ConfigurationExclusionManager.isConfigurationExcluded(config);
            System.out.println("  " + config + " -> " + 
                (excluded ? "🚫 EXCLUDED" : "✅ ALLOWED"));
        }
        
        // Remove an exclusion
        System.out.println("\nRemoving runtime exclusion...");
        ConfigurationExclusionManager.removeExcludedConfiguration("com.runtime.library.BadConfig");
        
        // Test after removal
        boolean stillExcluded = ConfigurationExclusionManager.isConfigurationExcluded("com.runtime.library.BadConfig");
        System.out.println("  com.runtime.library.BadConfig after removal -> " + 
            (stillExcluded ? "🚫 EXCLUDED" : "✅ ALLOWED"));
        
        System.out.println();
    }
    
    private static void demonstrateSpringContextIntegration() {
        System.out.println("3. SPRING CONTEXT INTEGRATION");
        System.out.println("------------------------------");
        
        // This demonstrates how exclusions work with the actual Spring context
        // Note: In a real scenario, you would initialize the Spring context
        
        System.out.println("Exclusions are automatically applied when SpringContextManager initializes:");
        System.out.println("  ✓ AutoConfigurationExcludeFilter uses ConfigurationExclusionManager");
        System.out.println("  ✓ SpringContextManager.isSafeAutoConfiguration() checks exclusions");
        System.out.println("  ✓ spring.factories loading respects exclusion rules");
        
        // Example of how to add exclusions before Spring context initialization
        System.out.println("\nTo exclude a library configuration before Spring startup:");
        System.out.println("  ConfigurationExclusionManager.addExcludedConfiguration(\"com.library.ProblemConfig\");");
        System.out.println("  SpringContextManager.getInstance().getSpringContext(); // Safe initialization");
        
        System.out.println();
    }
    
    private static void demonstrateDebuggingFeatures() {
        System.out.println("4. DEBUGGING & MONITORING");
        System.out.println("-------------------------");
        
        // Print current exclusion summary
        ConfigurationExclusionManager.printExclusionSummary();
        
        // Show individual exclusion lists
        System.out.println("\nDetailed exclusion lists:");
        System.out.println("Excluded Configurations: " + 
            ConfigurationExclusionManager.getExcludedConfigurations());
        System.out.println("Excluded Packages: " + 
            ConfigurationExclusionManager.getExcludedPackages());
        System.out.println("Excluded Patterns: " + 
            ConfigurationExclusionManager.getExcludedPatterns());
    }
    
    /**
     * Example of how to handle library-specific exclusions
     */
    public static class LibrarySpecificExclusions {
        
        /**
         * Exclude all configurations from a problematic library
         */
        public static void excludeEntireLibrary(String libraryPackage) {
            System.out.println("Excluding entire library: " + libraryPackage);
            ConfigurationExclusionManager.addExcludedPackage(libraryPackage);
        }
        
        /**
         * Exclude specific configuration types across all libraries
         */
        public static void excludeConfigurationType(String configType) {
            System.out.println("Excluding configuration type: " + configType);
            ConfigurationExclusionManager.addExcludedPattern(configType);
        }
        
        /**
         * Create a safe environment by excluding common problematic configurations
         */
        public static void createSafeSparkEnvironment() {
            System.out.println("Setting up safe Spark environment...");
            
            // Exclude web-related configurations
            ConfigurationExclusionManager.addExcludedPattern("WebConfig");
            ConfigurationExclusionManager.addExcludedPattern("ServletConfig");
            ConfigurationExclusionManager.addExcludedPattern("WebMvcConfig");
            
            // Exclude security configurations (often problematic in Spark)
            ConfigurationExclusionManager.addExcludedPattern("SecurityConfig");
            ConfigurationExclusionManager.addExcludedPattern("AuthConfig");
            
            // Exclude UI/management configurations
            ConfigurationExclusionManager.addExcludedPattern("ActuatorConfig");
            ConfigurationExclusionManager.addExcludedPattern("ManagementConfig");
            ConfigurationExclusionManager.addExcludedPattern("JmxConfig");
            
            System.out.println("Safe Spark environment configured!");
        }
    }
    
    /**
     * Example of conditional exclusions based on environment
     */
    public static class ConditionalExclusions {
        
        public static void setupEnvironmentSpecificExclusions() {
            String environment = System.getProperty("spring.profiles.active", "default");
            
            switch (environment) {
                case "spark":
                case "spark-partition":
                    // Exclude configurations that don't work well in Spark
                    ConfigurationExclusionManager.addExcludedPattern("WebConfig");
                    ConfigurationExclusionManager.addExcludedPattern("ServletConfig");
                    ConfigurationExclusionManager.addExcludedPattern("SecurityConfig");
                    System.out.println("Applied Spark-specific exclusions");
                    break;
                    
                case "test":
                    // More permissive in test environment
                    System.out.println("Test environment - minimal exclusions");
                    break;
                    
                case "production":
                    // Production-specific exclusions
                    ConfigurationExclusionManager.addExcludedPattern("TestConfig");
                    ConfigurationExclusionManager.addExcludedPattern("DevConfig");
                    System.out.println("Applied production-specific exclusions");
                    break;
                    
                default:
                    System.out.println("Default environment - standard exclusions");
            }
        }
    }
}
