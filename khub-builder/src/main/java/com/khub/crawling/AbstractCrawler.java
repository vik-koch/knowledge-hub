package com.khub.crawling;

import java.net.URL;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bson.Document;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.khub.common.AuthenticationHeader;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public abstract class AbstractCrawler implements Crawler {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    protected final HttpClient client = HttpClient.newHttpClient();

    protected final URL endpoint;
    protected final AuthenticationHeader requestHeader;

    public AbstractCrawler(URL endpoint, AuthenticationHeader requestHeader) {
        this.endpoint = endpoint;
        this.requestHeader = requestHeader;
    }

    /**
     * Starts the {@link Crawler} and writes retrieved data to
     * collections in the provided {@link MongoDatabase}. 
     * @param database - the {@link MongoDatabase} to write data to
     * @return true, if the step runned successfully, false otherwise
     */
    public boolean run(MongoDatabase database) {
        int retrievedDataSize = 0;

        Map<String, List<JsonElement>> data = run();
        for (String collectionName : data.keySet()) {
            MongoCollection<Document> collection = database.getCollection(collectionName);

            List<JsonElement> collectionData = data.get(collectionName);
            retrievedDataSize += collectionData.size();
            importCollection(collection, collectionData, collectionName);
        }

        return retrievedDataSize != 0;
    }

    /**
     * Imports the {@link List} of {@link JsonElement}s in the given {@link MongoCollection}
     * @param collection - the {@link MongoCollection}
     * @param data - the collection of {@link JsonElement}s
     * @param collectionName - the name of the {@link MongoCollection} to import
     */
    private void importCollection(MongoCollection<Document> collection, List<JsonElement> data, String collectionName) {
        if (data.size() == 0) return;
        List<Document> documents = new ArrayList<Document>();

        // Converts JSON to BSON document
        for (JsonElement jsonElement : data) {
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                continue;
            }
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            // Renames the id key and removes invalid BSON field names
            String jsonString = jsonObject.toString()
                .replaceAll("\"id\":", "\"_id\":")
                .replaceAll("@odata.", "odata");
            documents.add(Document.parse(jsonString));
        }

        // Inserts BSON documents to collection
        try {
            long size = collection.countDocuments();
            if (size != 0) {
                collection.deleteMany(new Document());
                logger.warning("The collection \"" + collectionName + "\" was not empty, removed " + size + " documents");
            }

            collection.insertMany(documents);
            logger.info(data.size() + " documents were successfully inserted into the collection \"" + collectionName + "\"");
        } catch (MongoException e) {
            logger.severe("Unable to insert the collection \"" + collectionName + "\"");
        }
    }

    /**
     * Logs initial information upon starting the task execution
     * @param taskName - the name of the current task
     */
    protected void logOnTaskStart(String taskName) {
        logger.info("Started to crawl " + taskName + "...");
    }

    /**
     * Logs information upon finishing a crawling request
     * @param taskName - the name of the current task
     * @param collection - the {@link List} currently processed
     * @param element - the {@link JsonElement} currently processed
     * @param result - the result {@link Collection}
     */
    protected void logOnSuccess(String taskName, List<JsonElement> collection, JsonElement element, Collection<JsonElement> result) {
        int index = collection.indexOf(element) + 1;
        logger.info("[" + index + "/" + collection.size() + "] Retrieved " + result.size() + " " + taskName);
    }

    /**
     * Logs final information upon finishing the task execution
     * @param taskName - the name of the current task
     * @param result - the result {@link Collection}
     */
    protected void logOnTaskFinish(String taskName, Collection<JsonElement> result) {
        if (!result.isEmpty()) {
            logger.info(result.size() + " " + taskName + " were retrieved");
        } else {
            logger.warning("No " + taskName + " were retrieved");
        }
    }

}