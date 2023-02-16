package com.khub.mapping;

import java.io.File;
import java.io.IOException;

import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.logging.Logger;

import com.khub.misc.DockerRunner;

public class RMLMapper {

    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Path mappingsPath;
    private final Path dockerPath;

    public RMLMapper(Path mappingsPath, Path dockerPath) {
        this.mappingsPath = mappingsPath;
        this.dockerPath = dockerPath;
    }

    /**
     * Executes {@code JSON}-to-{@code RML} mapping for the mapping file
     * with the provided {@code filename} located in {@code mappingPath}
     * @param filename - the mapping filename
     * @param outputDirectoryName - the name of the output folder
     */
    public void execute(String filename, String outputDirectoryName) {
        try {
            logger.info("Starting to map the file \"" + filename + "\"");

            String volume = mappingsPath.toRealPath(LinkOption.NOFOLLOW_LINKS) + ":/data";
            String output = outputDirectoryName + File.separator + filename;
            String format = "turtle";

            String[] command = {"docker-compose", "run", "--rm",
                                "-v", volume, "rml-mapper", "-m", filename, "-o", output, "-s", format };

            Process process = DockerRunner.runCommand(dockerPath, command);

            if (process.waitFor() == 0) {
                logger.info("The file \"" + filename + "\" was successfully mapped");
            } else {
                logger.severe("Unable to map the file \"" + filename + "\"");
            }

        } catch (IOException | SecurityException | InterruptedException e) {
            logger.severe("An error occured while trying to map \"" + filename + "\"");
        }
    }

}