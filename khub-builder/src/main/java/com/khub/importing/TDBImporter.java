package com.khub.importing;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.apache.jena.dboe.DBOpEnvException;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.tdb2.TDB2Factory;

import com.khub.common.FilesHelper;

public class TDBImporter {

    protected static final Logger logger = Logger.getLogger(TDBImporter.class.getName());

    private final Dataset tdb;

    private TDBImporter(Dataset tdb) {
        this.tdb = tdb;
    }

    /**
     * Returns an instance of {@link TDBImporter} if the given {@tdbPath}
     * is valid and the TDB store directory can be created
     * @param tdbPath - the {@link Path} to the TDB store
     * @return the {@link TDBImporter}
     */
    public static TDBImporter of(Path tdbPath) {
        try {
            FilesHelper.createDirectories(tdbPath);
            return new TDBImporter(TDB2Factory.connectDataset(tdbPath.toString()));
        } catch (DBOpEnvException e) {
            logger.severe("The TDB store at \"" + tdbPath + "\" is locked");
        }
        return null;
    }

    /**
     * Imports {@code .ttl} files from the given {@link Path} to the
     * {@link Model} with the given {@code modelName} in the TDB store
     * @param rdfPath - the {@link Path} to import from
     * @param modelName - the name of the {@link Model}
     * @return true, if the step runned successfully, false otherwise
     */
    public boolean importRDF(Path rdfPath, String modelName) {
        Path outputPath = rdfPath.resolve("output");
        Model model = tdb.getNamedModel(modelName);
        return importResources(outputPath, model, modelName);
    }

    /**
     * Imports {@code .owl} files from the given {@link Path} to the
     * {@link Model} with the given {@code modelName} in the TDB store
     * @param owlPath - the {@link Path} to import from
     * @param modelName - the name of the {@link Model}
     * @return true, if the step runned successfully, false otherwise
     */
    public boolean importOWL(Path owlPath, String modelName) {
        OntModel model = ModelFactory.createOntologyModel();
        return importResources(owlPath, model, modelName);
    }

    /**
     * Imports resources from the given {@link Path} to the
     * given {@link Model} with the given {@code modelName}
     * @param path - the {@link Path} to import from
     * @param model - the {@link Model} to import to
     * @param modelName - the name of the {@link Model}
     * @return true, if the step runned successfully, false otherwise
     */
    private boolean importResources(Path path, Model model, String modelName) {
        try {
            // Remove previous data from the model
            tdb.execute(() -> {
                long size = model.size();
                if (size != 0) {
                    logger.info("Removed " + size + " entries from the \"" + modelName + "\" model");
                    model.removeAll();
                }
            });

            List<String> filenames = FilesHelper.getFilenamesForPath(path);

            if (filenames.size() == 0) {
                logger.severe("No files for import were found at \"" + path + "\"");
                return false;
            }

            for (String filename : filenames) {
                tdb.executeWrite(() -> {
                    try {
                        model.read(path.toAbsolutePath().resolve(filename).toString());
                        tdb.addNamedModel(modelName, model);

                        logger.info("Imported \"" + filename + "\" to the \"" + modelName + "\" model");
                    } catch (Exception e) {
                        logger.severe("An error was thrown during transaction execution: " + e.getMessage());
                    }
                });
            }

            tdb.executeRead(() -> logger.info("Added " + model.size() + " entries to the \"" + modelName + "\" model"));
            return true;

        } catch (InvalidPathException | SecurityException e) {
            logger.severe("Unable to read files from \"" + path + "\" directory");
            return false;
        }
    }

}