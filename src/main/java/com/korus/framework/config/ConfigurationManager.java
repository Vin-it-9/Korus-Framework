package com.korus.framework.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigurationManager {

    private static ConfigurationManager instance;
    private final Map<String, String> properties = new HashMap<>();
    private final String activeProfile;

    private ConfigurationManager() {
        this.activeProfile = determineActiveProfile();
        loadProperties();
    }


    public int getPropertyCount() {
        return properties.size();
    }

    public Map<String, String> getAllProperties() {
        return new HashMap<>(properties);
    }


    public static ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }

    private String determineActiveProfile() {
        String profile = System.getProperty("spring.profiles.active");
        if (profile == null) profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profile == null) profile = "default";
        return profile;
    }

    private void loadProperties() {
        loadPropertiesFile("application.properties");
        if (!"default".equals(activeProfile)) loadPropertiesFile("application-" + activeProfile + ".properties");
        properties.putAll(System.getenv());
        System.getProperties().forEach((key, value) ->
                properties.put(key.toString(), value.toString()));
    }

    private void loadPropertiesFile(String fileName) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        if (inputStream != null) {
            try {
                Properties props = new Properties();
                props.load(inputStream);
                props.forEach((key, value) -> properties.put(key.toString(), value.toString()));
            } catch (IOException e) {
                System.err.println("❌ Failed to load: " + fileName + " - " + e.getMessage());
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    System.err.println("Failed to close stream for: " + fileName);
                }
            }
        } else {
            System.out.println("Configuration file not found: " + fileName);
        }
    }


    private void logProperty(String key, String defaultValue) {
        String value = properties.getOrDefault(key, defaultValue);
        System.out.println("   " + key + " = " + value);
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public int getIntProperty(String key, int defaultValue) {
        String value = properties.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.err.println("Invalid integer value for " + key + ": " + value);
            }
        }
        return defaultValue;
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.get(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    public String resolveValue(String valueExpression) {
        if (valueExpression.startsWith("${") && valueExpression.endsWith("}")) {
            String propertyKey = valueExpression.substring(2, valueExpression.length() - 1);
            String defaultValue = null;
            if (propertyKey.contains(":")) {
                String[] parts = propertyKey.split(":", 2);
                propertyKey = parts[0];
                defaultValue = parts[1];
            }
            return getProperty(propertyKey, defaultValue);
        }
        return valueExpression;
    }

    public Map<String, String> getPropertiesWithPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        String prefixWithDot = prefix.endsWith(".") ? prefix : prefix + ".";

        properties.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefixWithDot))
                .forEach(entry -> {
                    String key = entry.getKey().substring(prefixWithDot.length());
                    result.put(key, entry.getValue());
                });

        return result;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public void reload() {
        properties.clear();
        loadProperties();
    }
}
