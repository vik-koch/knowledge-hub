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

import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

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
        importOntology(configuration);
    }

    /**
     * Imports RDF resources to Apache Jena Dataset
     * @param configuration - {@code Properties} object
     */
    private void importResources(Properties configuration) {
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
                    Model model = tdb.getNamedModel("knowledge");
                    model.read(absolutePath + File.separator + fileName);
                    tdb.addNamedModel("knowledge", model);
                });
                logger.log(Level.INFO, "Finished reading " + fileName + " dataset");

            });
            tdb.end();

        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Invalid configuration provided", e);
            return;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "No files for import were found", e);
            return;
        }
    }

    /**
     * Imports RDF resources to Apache Jena Dataset
     * @param configuration - {@code Properties} object
     */
    private void importOntology(Properties configuration) {
        try {
            Dataset tdb = TDBHandler.getDataset(configuration);
            String absolutePath = new File(configuration.get("ontology.path").toString()).getAbsolutePath();

            OntModel model = ModelFactory.createOntologyModel();
            model.read(absolutePath);

            logger.log(Level.INFO, "Reading ontology");
            tdb.executeWrite(()->{
                tdb.addNamedModel("ontology", model);
            });
            logger.log(Level.INFO, "Finished reading ontology");

        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Invalid configuration provided", e);
            return;
        }
    }

    public void importContent(Properties configuration) {
        try {
            Dataset tdb = TDBHandler.getDataset(configuration);

            String path = configuration.getProperty("content.mappings.path");
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
                    Model model = ModelFactory.createDefaultModel();
                    model.read(absolutePath + File.separator + fileName);
                    tdb.addNamedModel("content", model);
                });
                logger.log(Level.INFO, "Finished reading " + fileName + " dataset");

            });
            tdb.end();

        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Invalid configuration provided", e);
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
        jr.importContent(configuration);
    }
}
