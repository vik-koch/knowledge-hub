package com.khub.jena;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.jena.query.Dataset;
import org.apache.jena.riot.RDFDataMgr;

import com.khub.exceptions.InvalidConfigurationException;
import com.khub.misc.Configuration;

public class JenaRunner {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Runs Jena importing process for writing RDF resources
     * @param configuration - {@code Properties} object
     */
    public void run(Properties configuration) {
        importResources(configuration);
    }

    /**
     * Imports RDF resources to Apache Jena Dataset
     * @param configuration
     */
    public void importResources(Properties configuration) {
        try {
            Dataset tdb = TDBHandler.getDataset(configuration);
            
            String path = configuration.getProperty("mappings.path");
            Path outputDir = Paths.get(path + File.separator + "output");

            List<String> fileNames = Files.list(outputDir)
                .filter(file -> !Files.isDirectory(file))
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toList());

            String absolutePath = new File(outputDir.toString()).getAbsolutePath();

            fileNames.forEach(fileName -> {
                logger.log(Level.INFO, "Reading " + fileName + " dataset");
                tdb.executeWrite(()->{
                    RDFDataMgr.read(tdb, absolutePath + File.separator + fileName);
                });
                tdb.commit();
                logger.log(Level.INFO, "Finished reading " + fileName + " dataset");

            });
            tdb.end();

        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Unable to start the jena runner", e);
            return;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "No files for import were found", e);
            return;
        }
    }

    // Temporary method for testing
    public static void main(String[] args) throws InvalidConfigurationException {
        String configPath = String.join(File.separator, "knowledge-hub", "src", "main", "resources", "config.properties");
        Properties configuration = Configuration.initialize(configPath);
        JenaRunner jr = new JenaRunner();
        jr.run(configuration);
    }
}
