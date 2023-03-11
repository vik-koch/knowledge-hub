package com.khub.common;

import java.util.logging.Logger;

import org.bson.BsonDocument;
import org.bson.BsonInt64;

import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoConnector {

    private static final Logger logger = Logger.getLogger(MongoConnector.class.getName());

    // Prevents instantiation
    private MongoConnector() {
    }

    /**
     * Returns a {@link MongoClient} instance for the given {@link ConnectionString} 
     * if the database is running, throws otherwise
     * @param connectionString - the {@link ConnectionString}
     * @return the {@link MongoClient}
     * @throws MongoException if no connection could be established
     */
    public static MongoClient getClient(ConnectionString connectionString) throws MongoException {
        try {
            MongoClient mongoClient = MongoClients.create(connectionString);
            MongoDatabase database = mongoClient.getDatabase("admin");
            database.runCommand(new BsonDocument("ping", new BsonInt64(1)));
            logger.info("Connection to MongoDB is establised successfully");
            return mongoClient;
        } catch (Exception e) {
            logger.severe("Unable to connect to MongoDB at \"" + connectionString + "\"");
            throw new MongoException("Unable to connect to Mongo DB", e);
        }
    }
}