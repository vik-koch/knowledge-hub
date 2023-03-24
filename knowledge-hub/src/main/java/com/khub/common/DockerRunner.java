package com.khub.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DockerRunner {

    private static final Logger logger = Logger.getLogger(DockerRunner.class.getName());

    // Prevents instantiation
    private DockerRunner() {
    }

    /**
     * Runs CLI Docker-compose command and returns the corresponding
     * {@link Process} or null, if an error occured while command execution
     * @param dockerPath - the {@link Path} to {@code docker-compose.yml}
     * @param command - the command to run
     * @return the {@link Process} with running docker
     */
    public static Process runCommand(Path dockerPath, String[] command) {
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