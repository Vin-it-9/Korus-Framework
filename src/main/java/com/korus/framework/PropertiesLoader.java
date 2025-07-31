package com.korus.framework;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesLoader {
    public static Properties load(String filename) {
        Properties props = new Properties();
        try (InputStream input = PropertiesLoader.class.getClassLoader().getResourceAsStream(filename)) {
            if (input == null) throw new IOException("File not found: " + filename);
            props.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Could not load " + filename, ex);
        }
        return props;
    }
}
