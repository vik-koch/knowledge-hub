package com.khub.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FilesHelper {

    private static final Logger logger = Logger.getLogger(FilesHelper.class.getName());

    // Prevents instantiation
    private FilesHelper() {
    }

    /**
     * Retrieves {@code filenames} as {@link List} of {@link String}s of
     * the files in the directory under the given {@link Path}
     * @param path - the {@link Path} to get files from
     * @return the collection of {@code filenames}
     */
    public static List<String> getFilenamesForPath(Path path) {
        try {
            return Files.list(path)
            .filter(file -> !Files.isDirectory(file))
            .map(Path::getFileName)
            .map(Path::toString)
            .collect(Collectors.toList());

        } catch (SecurityException | IOException e) {
            logger.warning("Unable to retrieve files under \"" + path + "\"");
            return List.of();
        }
    }

    /**
     * Checks if the given {@code path} exists and tries to create directories for it
     * @param path - the {@link Path} to create directory for
     * @return the given {@link Path}, if a directory exists or was created, null otherwise
     */
    public static Path createDirectories(Path path) {
        try {
            return Files.createDirectories(path);
        } catch (Exception e) {
            logger.severe("Unable to create output folders under \"" + path + "\"");
            return null;
        }
    }
}