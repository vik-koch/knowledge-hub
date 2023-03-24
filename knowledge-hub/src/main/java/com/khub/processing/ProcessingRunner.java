package com.khub.processing;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.Document;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.khub.exceptions.InvalidConfigurationException;
import com.khub.misc.Configuration;
import com.khub.misc.MongoConnector;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class ProcessingRunner {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Runs metadata processing for collections from {@code MongoDB} database named {@code raw_data}
     * and writes the processed collections to a new {@code processed_data} database
     * @param configuration - {@code Properties} object
     */
    public void run(Properties configuration) {
        // Prepares mongo databases
        MongoClient mongoClient = null;
        try {
            mongoClient = MongoConnector.getClient(configuration);
        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Unable to start the processing runner", e);
            return;
        }
        MongoDatabase sourceDataBase = mongoClient.getDatabase("raw_data");
        MongoDatabase outputDataBase = mongoClient.getDatabase("processed_data");

        // Retrieves collection names from source database
        List<String> collectionNames = new ArrayList<String>(); 
        sourceDataBase.listCollectionNames().forEach((Consumer<String>) (name -> collectionNames.add(name)));

        // Concurrently processes metadata fields for each collection
        collectionNames.parallelStream().forEach(collectionName -> {
            MongoCollection<Document> sourceCollection = sourceDataBase.getCollection(collectionName);
            MongoCollection<Document> outputCollection = outputDataBase.getCollection(collectionName);

            List<JsonElement> processedElements = MetadataProcessor.process(convertToJsonElements(sourceCollection));
            outputCollection.insertMany(convertToDocuments(processedElements));
        });

        mongoClient.close();
    }

    /**
     * Converts a {@code MongoCollection} of {@code Documents} to a {@code List} of {@code JsonElements}
     * @param documents - a {@code MongoCollection} of {@code Documents}
     * @return the {@code List} of {@code JsonElements}
     */
    private List<JsonElement> convertToJsonElements(MongoCollection<Document> documents) {
        List<JsonElement> elements = new ArrayList<JsonElement>();

        for (Document document : documents.find()) {
            elements.add(JsonParser.parseString(document.toJson()));
        }

        return elements;
    }

    /**
     * Converts a {@code List} of {@code JsonElements} to a {@code List} of {@code Documents}
     * @param documents - a {@code List} of {@code JsonElements}
     * @return the {@code List} of {@code Documents}
     */
    private List<Document> convertToDocuments(List<JsonElement> elements) {
        List<Document> documents = new ArrayList<Document>();

        for (JsonElement element : elements) {
            documents.add(Document.parse(element.toString()));
        }

        return documents;
    }

    // Temporary method for testing
    public static void main(String[] args) throws InvalidConfigurationException {
        String configPath = String.join(File.separator, "knowledge-hub", "src", "main", "resources", "config.properties");
        Properties configuration = Configuration.initialize(configPath);
        ProcessingRunner pr = new ProcessingRunner();
        pr.run(configuration);
    }
}
