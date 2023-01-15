package com.khub.mapping;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.Document;

import com.mongodb.client.MongoCollection;

import be.ugent.rml.Executor;
import be.ugent.rml.records.RecordsFactory;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.store.QuadStoreFactory;
import be.ugent.rml.store.RDF4JStore;

/**
 * Mapper for converting semi-structured data to RDF
 */
public class RMLMapper {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());
    private String path = null;

    // Constructor
    public RMLMapper(String path) {
        this.path = path;
    }

    /**
     * Exports MongoDB BSON collection to a JSON file in the given {@code path}
     * @param collection - MongoCollection with BSON documents
     */
    public void exportCollection(MongoCollection<Document> collection) {
        String collectionName = collection.getNamespace().getCollectionName();
        logger.log(Level.INFO, "Starting to export the collection " + collectionName);

        List<String> output = new ArrayList<String>();
        output.add("[");

        // Converts each BSON to JSON String
        for (Document document : collection.find()) {
            output.add(document.toJson());
        }

        output.add("]");

        try {
            Files.write(Paths.get(path + File.separator + collectionName + ".json"), output);
            logger.log(Level.INFO, "The collection " + collectionName + " was successfully exported");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to export the collection " + collectionName, e);
        }
    }

    /**
     * Executes mapping for the given TTL mapping file
     * @param mappingName - name of mapping file
     */
    public void executeMapping(String mappingName) {
        try (FileWriter out = new FileWriter(path + File.separator + "_" + mappingName + "-output.ttl")) {
            File mapping = new File(path + File.separator + mappingName + ".ttl");
            logger.log(Level.INFO, "Starting to map the file " + mappingName);

            if (!mapping.isFile()) {
                throw new FileNotFoundException("No file found at " + path);
            }

            // Loads the mapping in a QuadStore and creates a RecordsFactory
            QuadStore rmlStore = QuadStoreFactory.read(new FileInputStream(mapping));
            RecordsFactory factory = new RecordsFactory(mapping.getParent());

            // Creates an output QuadStore and copies prefixes
            QuadStore result = new RDF4JStore();
            result.copyNameSpaces(rmlStore);

            // Creates a mapping Executor and runs it
            Executor executor = new Executor(rmlStore, factory, result, null, null);
            executor.execute(null);

            // Generates output file
            result.write(out, "turtle");
            logger.log(Level.INFO, "The file " + mappingName + " was successfully mapped");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unable to map the file " + mappingName, e);
        }
    }
}
