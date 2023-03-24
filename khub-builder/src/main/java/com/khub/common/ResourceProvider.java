package com.khub.common;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Logger;

public class ResourceProvider {

    private static final Logger logger = Logger.getLogger(ResourceProvider.class.getName());

    private static final String PROPERTIES_RESOURCE = "/config.properties";

    // Prevents instantiation
    private ResourceProvider() {
    }

    /**
     * Loads the user application {@link Properties} from the given {@code configPath}
     * and creates default configuration file if no custom file was found
     * @param configPath - the {@link Path} to config.properties
     * @return the {@link Properties} or null
     */
    public static Properties loadProperties(Path configPath) {
        try {
            Properties configuration = new Properties();

            if (!Files.exists(configPath)) {
                copyFromResourceToPath(PROPERTIES_RESOURCE, configPath);
            }
            configuration.load(new FileInputStream(configPath.toFile()));

            logger.info("Configuration is successfully initialized");
            return configuration;

        } catch (Exception e) {
            logger.severe("Unable to load the configuration from \"" + configPath + "\"");
            return null;
        }
    }

    /**
     * Copies default file from Java resources to the given {@code outputPath}
     * @param resourcePath - the predefined resource path to copy from
     * @param outputPath - the {@link Path} to copy to
     */
    public static void copyFromResourceToPath(String resourcePath, Path outputPath) {
        try {
            FilesHelper.createDirectories(outputPath.getParent());
            InputStream stream = ResourceProvider.class.getResourceAsStream(resourcePath);
            Files.copy(stream, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            logger.severe("Unable to copy resource from \"" + resourcePath + "\" to \"" + outputPath + "\"");
        }
    }

}