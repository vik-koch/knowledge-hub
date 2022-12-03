package com.khub.misc;

import java.util.Properties;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

// Wrapper class for MongoClient
public class MongoConnector {

    private static final String URL_KEY = "mongo.url";

    // Prevents instantiation
    private MongoConnector() {
    }

    /**
     * Returns a {@code MongoClient} instance created from the parsed {@code Properties}
     * @param configuration - {@code Properties} object
     * @return a {@code MongoClient} instance
     */
    public static MongoClient getClient(Properties configuration) {
        String url = configuration.getProperty(URL_KEY);
        System.out.println(url);
        return MongoClients.create(url);
    }
}
