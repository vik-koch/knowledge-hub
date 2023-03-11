package com.khub.common;

import java.io.BufferedReader;
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
    private static final String DOCKER_COMPOSE_RESOURCE = "/docker-compose.yml";

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
     * Starts docker container by running docker-compose command on the
     * {@code yml} configuration provided in the given {@code dockerPath} 
     * and creates default {@code yml} configuration file if no file was found
     * @param dockerPath - the {@link Path} to docker-compose.yml
     * @return the {@link Process} with the running command or null
     */
    public static Process startDockerCompose(Path dockerPath) {
        try {
            if (!Files.exists(dockerPath)) {
                copyFromResourceToPath(DOCKER_COMPOSE_RESOURCE, dockerPath);
            }

            String[] composeCommand = {"docker-compose", "-p", "knowledge-hub", "up"};
            Process docker = DockerRunner.runCommand(dockerPath, composeCommand);

            // Check if process terminated with an error
            if (docker != null && !docker.isAlive() && docker.exitValue() != 0) {
                BufferedReader errorReader = docker.errorReader();
                StringBuilder errorMessage = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorMessage.append(line);
                }
                throw new Exception(errorMessage.toString());
            }
            logger.info("Docker Compose started successfully");
            return docker;

        } catch (Exception e) {
            logger.severe("Unable to start Docker Compose from \"" + dockerPath + "\"");
            logger.severe(e.getMessage());
            return null;
        }
    }

    /**
     * Copies default file from Java resources to the given {@code outputPath}
     * @param resourcePath - the predefined resource path to copy from
     * @param outputPath - the {@link Path} to copy to
     */
    private static void copyFromResourceToPath(String resourcePath, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            InputStream stream = ResourceProvider.class.getResourceAsStream(resourcePath);
            Files.copy(stream, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            logger.severe("Unable to copy resource from \"" + resourcePath + "\" to \"" + outputPath + "\"");
        }
    }

}