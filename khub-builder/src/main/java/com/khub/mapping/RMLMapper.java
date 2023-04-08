package com.khub.mapping;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * @return true, if the step runned successfully, false otherwise
     */
    public boolean run(Path mappingsPath, String outputDirectoryName) {

        List<String> filenames = FilesHelper.getFilenamesForPath(mappingsPath);
        if (filenames.size() == 0) {
            logger.severe("No mapping files were found at \"" + mappingsPath + "\"");
            return false;
        }

        Path outputPath = FilesHelper.createDirectories(mappingsPath.resolve(outputDirectoryName));
        if (outputPath == null) {
            return false;
        }

        logger.info("Retrieved " + filenames.size() + " mapping files: " + String.join(", ", filenames));
        Pattern pattern = Pattern.compile("source\s*\"(.*)\"");

        filenames.stream().forEach(filename -> {
            if (filename.endsWith(".ttl")) {
                try {
                    Path absoluteMappingsPath = mappingsPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
                    Path filePath = absoluteMappingsPath.resolve(filename);
                    String content = Files.readString(filePath);
                    Matcher matcher = pattern.matcher(content);
                    matcher.find();
                    Path sourcePath = mappingsPath.resolve((matcher.group(1)));
                    if (Files.exists(sourcePath)) {
                        execute(absoluteMappingsPath, filename, outputDirectoryName);
                    } else {
                        logger.warning("Unable to find the source file at \"" + sourcePath + "\"");
                    }
                } catch (Exception e) {
                    logger.warning("Unable to parse the source file given in \"" + filename + "\"");
                }
            }
        });

        return true;
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

            String[] command = {"docker-compose", "run", "-rm", "--use-aliases",
                                "-v", volume, "rml-mapper", "-m", filename, "-o", output, "-s", format };

            Process process = DockerRunner.runCommand(dockerPath, command);

            if (process.waitFor() == 0) {
                logger.info("The file \"" + filename + "\" was successfully mapped");
            } else {
                logger.severe("Unable to map the file \"" + filename + "\"");
            }

            process.destroy();

        } catch (SecurityException | InterruptedException e) {
            logger.severe("An error occured while trying to map \"" + filename + "\"");
        }
    }

}