package com.khub.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DockerRunner {

    private static final Logger logger = Logger.getLogger(DockerRunner.class.getName());

    private static final String DOCKER_COMPOSE_RESOURCE = "/docker-compose.yml";

    // Prevents instantiation
    private DockerRunner() {
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
     * Stops docker container by running docker-compose command on the
     * {@code yml} configuration provided in the given {@code dockerPath} 
     * @param dockerPath - the {@link Path} to docker-compose.yml
     * @return the {@link Process} with the running command or null
     */
    public static Process stopDockerCompose(Path dockerPath) {
        try {
            String[] composeCommand = {"docker-compose", "-p", "knowledge-hub", "stop"};
            Process docker = DockerRunner.runCommand(dockerPath, composeCommand);
            return docker;

        } catch (Exception e) {
            logger.warning("Unable to stop Docker Compose from \"" + dockerPath + "\"");
            return null;
        }
    }

    /**
     * Runs CLI Docker-compose command and returns the corresponding
     * {@link Process} or null, if an error occured while command execution
     * @param dockerPath - the {@link Path} to {@code docker-compose.yml}
     * @param command - the command to run
     * @return the {@link Process} with running docker
     */
    public static Process runCommand(Path dockerPath, String[] command) {

        if (!Files.exists(dockerPath)) {
            ResourceProvider.copyFromResourceToPath(DOCKER_COMPOSE_RESOURCE, dockerPath);
        }

        try {
            File directory = new File(dockerPath.getParent().toString());
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(directory);

            Process process = processBuilder.start();
            process.waitFor(3, TimeUnit.SECONDS);
            return process;

        } catch (InterruptedException | IOException e) {
            logger.severe("Failed to run the given command: \"" + command + "\"");
            return null;
        }
    }

}