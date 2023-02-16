package com.khub.crawling;

import java.net.URL;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bson.Document;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
     */
    public void run(MongoDatabase database) {

        Map<String, List<JsonElement>> data = run();
        for (String collectionName : data.keySet()) {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            importCollection(collection, data.get(collectionName));
        }
    }

    /**
     * Imports the {@link List} of {@link JsonElement}s in the given {@link MongoCollection}
     * @param collection - the {@link MongoCollection}
     * @param data - the collection of {@link JsonElement}s
     */
    private void importCollection(MongoCollection<Document> collection, List<JsonElement> data) {
        if (data.size() == 0) return;
        List<Document> documents = new ArrayList<Document>();

        // Converts JSON to BSON document
        for (JsonElement jsonElement : data) {
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                continue;
            }
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            // Renames id key to MongoDb _id key
            if (jsonObject.has("id")) {
                jsonObject.addProperty("_id", jsonObject.get("id").getAsString());
                jsonObject.remove("id");
            }
            documents.add(Document.parse(jsonObject.toString()));
        }

        // Inserts BSON documents to collection
        try {
            collection.insertMany(documents);
            logger.info(data.size() + " documents were sucessfully inserted into the \"" + collection.getNamespace() + "\" collection");
        } catch (MongoException e) {
            logger.severe("Unable to insert the \"" + collection.getNamespace() + "\" collection");
        }
    }

}