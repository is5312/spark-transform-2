package com.sparktransform.sparkjob;

import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Utility class for managing configuration exclusions in Spring contexts
 * Provides centralized logic for excluding library @Configuration classes
 * without modifying the library code itself.
 */
public class ConfigurationExclusionManager {
    
    // Exact configuration class names to exclude
    private static final Set<String> EXCLUDED_CONFIGURATIONS = new HashSet<>();
    
    // Package patterns to exclude
    private static final Set<String> EXCLUDED_PACKAGES = new HashSet<>();
    
    // Class name patterns to exclude
    private static final Set<String> EXCLUDED_PATTERNS = new HashSet<>();
    
    // Flag to track if exclusions have been loaded from properties
    private static volatile boolean exclusionsLoaded = false;
    
    static {
        loadExclusionsFromProperties();
    }
    
    /**
     * Check if a configuration class should be excluded based on its class name
     * 
     * @param className The fully qualified class name
     * @return true if the configuration should be excluded, false otherwise
     */
    public static boolean isConfigurationExcluded(String className) {
        if (className == null || className.trim().isEmpty()) {
            return false;
        }
        
        // Ensure exclusions are loaded
        if (!exclusionsLoaded) {
            loadExclusionsFromProperties();
        }
        
        // Check exact class name matches
        if (EXCLUDED_CONFIGURATIONS.contains(className)) {
            logExclusion("exact match", className);
            return true;
        }
        
        // Check package-level exclusions
        for (String excludedPackage : EXCLUDED_PACKAGES) {
            if (className.startsWith(excludedPackage)) {
                logExclusion("package match (" + excludedPackage + ")", className);
                return true;
            }
        }
        
        // Check pattern-based exclusions
        for (String pattern : EXCLUDED_PATTERNS) {
            if (className.contains(pattern)) {
                logExclusion("pattern match (" + pattern + ")", className);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a configuration class should be excluded based on MetadataReader
     * 
     * @param metadataReader The Spring MetadataReader for the class
     * @return true if the configuration should be excluded, false otherwise
     */
    public static boolean isConfigurationExcluded(MetadataReader metadataReader) {
        if (metadataReader == null) {
            return false;
        }
        
        String className = metadataReader.getClassMetadata().getClassName();
        return isConfigurationExcluded(className);
    }
    
    /**
     * Add a configuration class to the exclusion list at runtime
     * 
     * @param className The fully qualified class name to exclude
     */
    public static void addExcludedConfiguration(String className) {
        if (className != null && !className.trim().isEmpty()) {
            EXCLUDED_CONFIGURATIONS.add(className);
            System.out.println("➕ ConfigurationExclusionManager: Added configuration to exclusion list: " + className);
        }
    }
    
    /**
     * Add a package pattern to the exclusion list at runtime
     * 
     * @param packagePattern The package pattern to exclude
     */
    public static void addExcludedPackage(String packagePattern) {
        if (packagePattern != null && !packagePattern.trim().isEmpty()) {
            EXCLUDED_PACKAGES.add(packagePattern);
            System.out.println("➕ ConfigurationExclusionManager: Added package to exclusion list: " + packagePattern);
        }
    }
    
    /**
     * Add a class name pattern to the exclusion list at runtime
     * 
     * @param pattern The pattern to exclude (checked with contains())
     */
    public static void addExcludedPattern(String pattern) {
        if (pattern != null && !pattern.trim().isEmpty()) {
            EXCLUDED_PATTERNS.add(pattern);
            System.out.println("➕ ConfigurationExclusionManager: Added pattern to exclusion list: " + pattern);
        }
    }
    
    /**
     * Remove a configuration class from the exclusion list
     * 
     * @param className The fully qualified class name to remove from exclusions
     */
    public static void removeExcludedConfiguration(String className) {
        if (EXCLUDED_CONFIGURATIONS.remove(className)) {
            System.out.println("➖ ConfigurationExclusionManager: Removed configuration from exclusion list: " + className);
        }
    }
    
    /**
     * Get all currently excluded configuration class names
     * 
     * @return A copy of the set of excluded configuration class names
     */
    public static Set<String> getExcludedConfigurations() {
        return new HashSet<>(EXCLUDED_CONFIGURATIONS);
    }
    
    /**
     * Get all currently excluded package patterns
     * 
     * @return A copy of the set of excluded package patterns
     */
    public static Set<String> getExcludedPackages() {
        return new HashSet<>(EXCLUDED_PACKAGES);
    }
    
    /**
     * Get all currently excluded class name patterns
     * 
     * @return A copy of the set of excluded class name patterns
     */
    public static Set<String> getExcludedPatterns() {
        return new HashSet<>(EXCLUDED_PATTERNS);
    }
    
    /**
     * Clear all exclusion rules (use with caution)
     */
    public static void clearAllExclusions() {
        EXCLUDED_CONFIGURATIONS.clear();
        EXCLUDED_PACKAGES.clear();
        EXCLUDED_PATTERNS.clear();
        System.out.println("🧹 ConfigurationExclusionManager: Cleared all exclusion rules");
    }
    
    /**
     * Load exclusions from application properties
     */
    private static void loadExclusionsFromProperties() {
        if (exclusionsLoaded) {
            return;
        }
        
        try {
            // Load from main application.properties
            loadExclusionsFromResource("application.properties");
            
            // Load from profile-specific properties
            String activeProfiles = System.getProperty("spring.profiles.active", "partition,autoconfigure");
            for (String profile : activeProfiles.split(",")) {
                profile = profile.trim();
                loadExclusionsFromResource("application-" + profile + ".properties");
            }
            
            // Load default exclusions if none were loaded from properties
            if (EXCLUDED_CONFIGURATIONS.isEmpty() && EXCLUDED_PACKAGES.isEmpty() && EXCLUDED_PATTERNS.isEmpty()) {
                loadDefaultExclusions();
            }
            
            exclusionsLoaded = true;
            System.out.println("✅ ConfigurationExclusionManager: Loaded exclusions from properties");
            
        } catch (Exception e) {
            System.out.println("⚠️ ConfigurationExclusionManager: Error loading exclusions from properties: " + e.getMessage());
            loadDefaultExclusions();
            exclusionsLoaded = true;
        }
    }
    
    /**
     * Load exclusions from a specific properties resource
     */
    private static void loadExclusionsFromResource(String resourceName) {
        try {
            ClassPathResource resource = new ClassPathResource(resourceName);
            if (resource.exists()) {
                Properties props = PropertiesLoaderUtils.loadProperties(resource);
                
                // Load configuration exclusions
                String configurations = props.getProperty("exclusions.configurations", "");
                if (!configurations.trim().isEmpty()) {
                    String[] configs = configurations.split(",");
                    for (String config : configs) {
                        config = config.trim();
                        if (!config.isEmpty()) {
                            EXCLUDED_CONFIGURATIONS.add(config);
                        }
                    }
                }
                
                // Load package exclusions
                String packages = props.getProperty("exclusions.packages", "");
                if (!packages.trim().isEmpty()) {
                    String[] pkgs = packages.split(",");
                    for (String pkg : pkgs) {
                        pkg = pkg.trim();
                        if (!pkg.isEmpty()) {
                            EXCLUDED_PACKAGES.add(pkg);
                        }
                    }
                }
                
                // Load pattern exclusions
                String patterns = props.getProperty("exclusions.patterns", "");
                if (!patterns.trim().isEmpty()) {
                    String[] ptns = patterns.split(",");
                    for (String ptn : ptns) {
                        ptn = ptn.trim();
                        if (!ptn.isEmpty()) {
                            EXCLUDED_PATTERNS.add(ptn);
                        }
                    }
                }
                
                System.out.println("✅ ConfigurationExclusionManager: Loaded exclusions from " + resourceName);
            }
        } catch (IOException e) {
            System.out.println("⚠️ ConfigurationExclusionManager: Could not load " + resourceName + ": " + e.getMessage());
        }
    }
    
    /**
     * Load default exclusions when no properties are available
     */
    private static void loadDefaultExclusions() {
        // Default configuration exclusions
        EXCLUDED_CONFIGURATIONS.addAll(Arrays.asList(
            "com.some.library.UnwantedConfiguration",
            "com.another.library.ProblematicConfig",
            "com.library.SecurityConfiguration",
            "com.thirdparty.AutoConfig"
        ));
        
        // Default package exclusions
        EXCLUDED_PACKAGES.addAll(Arrays.asList(
            "com.problematic.library",
            "com.unwanted.package",
            "com.thirdparty.security"
        ));
        
        // Default pattern exclusions
        EXCLUDED_PATTERNS.addAll(Arrays.asList(
            "SecurityConfig",
            "WebConfig",
            "ServletConfig"
        ));
        
        System.out.println("✅ ConfigurationExclusionManager: Loaded default exclusions");
    }
    
    /**
     * Reload exclusions from properties (useful for testing or runtime changes)
     */
    public static void reloadExclusionsFromProperties() {
        EXCLUDED_CONFIGURATIONS.clear();
        EXCLUDED_PACKAGES.clear();
        EXCLUDED_PATTERNS.clear();
        exclusionsLoaded = false;
        loadExclusionsFromProperties();
    }
    
    /**
     * Log exclusion information
     */
    private static void logExclusion(String reason, String className) {
        System.out.println("🚫 ConfigurationExclusionManager: Excluding configuration [" + reason + "]: " + className);
    }
    
    /**
     * Print current exclusion configuration for debugging
     */
    public static void printExclusionSummary() {
        System.out.println("📋 ConfigurationExclusionManager Summary:");
        System.out.println("   Excluded Configurations: " + EXCLUDED_CONFIGURATIONS.size());
        EXCLUDED_CONFIGURATIONS.forEach(config -> System.out.println("     - " + config));
        
        System.out.println("   Excluded Packages: " + EXCLUDED_PACKAGES.size());
        EXCLUDED_PACKAGES.forEach(pkg -> System.out.println("     - " + pkg + ".*"));
        
        System.out.println("   Excluded Patterns: " + EXCLUDED_PATTERNS.size());
        EXCLUDED_PATTERNS.forEach(pattern -> System.out.println("     - *" + pattern + "*"));
    }
}
