package com.khub.mapping;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import com.khub.common.DockerRunner;
import com.khub.common.FilesHelper;

public class RMLMapper {

    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Path dockerPath;

    public RMLMapper(Path dockerPath) {
        this.dockerPath = dockerPath;
    }

    /**
     * Starts {@link RMLMapper} for {@code JSON}-to-{@code RML} mapping of data using
     * the defined {@code RML} mappings provided in the {@code mappingsPath} and saves
     * the mapped files to the folder with {@code outputDirectoryName} under the same {@link Path}
     * @param mappingsPath - the {@link Path} to mappings files
     * @param outputDirectoryName - the directory name to save mapped files to
     * @return true if the step runned successfully, false otherwise
     */
    public boolean run(Path mappingsPath, String outputDirectoryName) {
        try {
            List<String> filenames = FilesHelper.getFilenamesForPath(mappingsPath);
            Files.createDirectories(mappingsPath.resolve(outputDirectoryName));
            Path absoluteMappingsPath = mappingsPath.toRealPath(LinkOption.NOFOLLOW_LINKS);

            filenames.parallelStream().forEach(filename -> {
                if (filename.endsWith(".ttl")) {
                    execute(absoluteMappingsPath, filename, outputDirectoryName);
                }
            });
            return true;

        } catch (Exception e) {
            logger.severe("Unable to create output folders under \"" + mappingsPath.resolve(outputDirectoryName) + "\"");
            return false;
        }
    }

    /**
     * Executes {@code JSON}-to-{@code RML} mapping for the mapping file
     * with the provided {@code filename} located in {@code mappingPath}
     * @param absoluteMappingsPath - the absolute {@link Path} to mappings files
     * @param filename - the mapping filename
     * @param outputDirectoryName - the name of the output folder
     */
    public void execute(Path absoluteMappingsPath, String filename, String outputDirectoryName) {
        try {
            logger.info("Started to map the file \"" + filename + "\"");

            String volume = absoluteMappingsPath + ":/data";
            String output = Paths.get(outputDirectoryName, filename).toString();
            String format = "turtle";

            String[] command = {"docker-compose", "run", "--rm",
                                "-v", volume, "rml-mapper", "-m", filename, "-o", output, "-s", format };

            Process process = DockerRunner.runCommand(dockerPath, command);

            if (process.waitFor() == 0) {
                logger.info("The file \"" + filename + "\" was successfully mapped");
            } else {
                logger.severe("Unable to map the file \"" + filename + "\"");
            }

        } catch (SecurityException | InterruptedException e) {
            logger.severe("An error occured while trying to map \"" + filename + "\"");
        }
    }

}