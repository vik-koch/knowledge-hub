package com.khub.crawling;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.Document;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.khub.exceptions.InvalidConfigurationException;
import com.khub.misc.Configuration;
import com.khub.misc.MongoConnector;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * Runner for crawling processes
 */
public class CrawlingRunner {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Runs crawling process for {@see ConfluenceCrawler} and {@see TeamsCrawler} and writes
     * the retrieved data in {@code MongoDB} instance in {@code raw_data} database
     * @param configuration - {@code Properties} object
     */
    public void run(Properties configuration) {
        // Prepares mongo database
        MongoClient mongoClient = null;
        try {
            mongoClient = MongoConnector.getClient(configuration);
        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Unable to start the crawling runner", e);
            return;
        }
        MongoDatabase database = mongoClient.getDatabase("raw_data");

        // Starts Confluence Crawler
        try {
            ConfluenceCrawler confluenceCrawler = ConfluenceCrawler.of(configuration);
            Map<String, List<JsonElement>> confluenceData = confluenceCrawler.run();
            for (String name : confluenceData.keySet()) {
                writeToDb(database, name, new HashSet<>(confluenceData.get(name)));
            }
        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Unable to start a Confluence crawler", e);
        }        

        // Starts Teams Crawler
        try {
            TeamsCrawler teamsCrawler = TeamsCrawler.of(configuration);
            Map<String, List<JsonElement>> teamsData = teamsCrawler.run();
            for (String name : teamsData.keySet()) {
                writeToDb(database, name, new HashSet<>(teamsData.get(name)));
            }
        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Unable to start a Teams crawler", e);
        }

        mongoClient.close();
    }

    /**
     * Converts a {@code JsonElement} to a {@code Document} and writes to 
     * a {@code Collection} in the given database
     * @param database - a {@see MongoDatabase} instance
     * @param name - the {@code Collection} name
     * @param data - the {@code Collection} data
     */
    private void writeToDb(MongoDatabase database, String name, HashSet<JsonElement> data) {
        if (data.size() == 0) return;

        MongoCollection<Document> collection = database.getCollection(name);
        List<Document> documents = new ArrayList<Document>();

        // Converts JSON to BSON document
        for (JsonElement jsonElement : data) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            if (jsonObject.has("id")) {
                jsonObject.addProperty("_id", jsonObject.get("id").getAsString());
                jsonObject.remove("id");
            }
            documents.add(Document.parse(jsonObject.toString()));
        }

        // Inserts BSON documents to MongoDB
        try {
            collection.insertMany(documents);
            logger.log(Level.INFO, data.size() + " documents were sucessfully inserted to the " + name + " collection");
        } catch (MongoException e) {
            logger.log(Level.SEVERE, "Unable to insert due to an error: ", e);
        }
    }

    // Temporary method for testing
    public static void main(String[] args) throws InvalidConfigurationException {
        String configPath = String.join(File.separator, "knowledge-hub", "src", "main", "resources", "config.properties");
        Properties configuration = Configuration.initialize(configPath);
        CrawlingRunner cr = new CrawlingRunner();
        cr.run(configuration);
    }
}