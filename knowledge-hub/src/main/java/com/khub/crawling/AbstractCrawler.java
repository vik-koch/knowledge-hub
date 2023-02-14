package com.khub.crawling;

import java.net.URL;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bson.Document;

import com.google.gson.JsonElement;
import com.khub.misc.MongoConnector;
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
            MongoConnector.importCollection(collection, data.get(collectionName));
        }
    }

}