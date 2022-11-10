package com.khub.misc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Configuration {

    private static final Logger logger = Logger.getLogger(Configuration.class.getName());

    // Prevents instantiation
    private Configuration() {
    }

    /**
     * Initializes the application configuration from the given path
     * @param configPath - path to the configuration
     * @return application configuration or null
     */
    public static Properties initialize(String configPath) {
        Properties configuration = new Properties();
        try {
            configuration.load(new FileInputStream(configPath));
            logger.log(Level.INFO, "Configuration initialized");

        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "No file found at " + configPath, e);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to read the file", e);

        }
        return configuration;
    }
}