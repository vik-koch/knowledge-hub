package com.khub.mapping;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.Document;

import com.khub.exceptions.InvalidConfigurationException;
import com.khub.misc.Configuration;
import com.khub.misc.MongoConnector;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MetadataMappingRunner {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Runs metadata mapping process for collections from {@code MongoDB} database
     * named {@code processed_data} and writes mapped files to the path under
     * {@code mappings.path} given in {@code configuration}
     * @param configuration - {@code Properties} object
     */
    public void run(Properties configuration) {
        // Prepares mongo databases
        MongoClient mongoClient = null;
        try {
            mongoClient = MongoConnector.getClient(configuration);
        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Unable to start the metadata mapping runner", e);
            return;
        }
        MongoDatabase dataBase = mongoClient.getDatabase("processed_data");

        // Retrieves collection names
        List<String> collectionNames = new ArrayList<String>(); 
        dataBase.listCollectionNames().forEach((Consumer<String>) (name -> collectionNames.add(name)));

        // Initializes RML mapper with the path to mappings
        String path = configuration.getProperty("mappings.path");
        RMLMapper mapper = new RMLMapper(path);

        // Concurrently processes metadata fields for each collection
        collectionNames.forEach(collectionName -> {
            MongoCollection<Document> collection = dataBase.getCollection(collectionName);
            mapper.exportCollection(collection);
            mapper.executeMapping(collection.getNamespace().getCollectionName());
        });

        mongoClient.close();
    }

    // Temporary method for testing
    public static void main(String[] args) throws InvalidConfigurationException {
        String configPath = String.join(File.separator, "knowledge-hub", "src", "main", "resources", "config.properties");
        Properties configuration = Configuration.initialize(configPath);
        MetadataMappingRunner mmr = new MetadataMappingRunner();
        mmr.run(configuration);
    }
}
