package com.khub.exporting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bson.Document;

import com.khub.common.FilesHelper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoExporter {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Starts {@link MongoExporter} for exporting retrieved collections as {@link JSON}
     * to the folder with {@code outputDirectoryName} under the given {@code knowledgePath}
     * @param database - the {@link MongoDatabase} to read data from
     * @param knowledgePath - the {@link Path} for {@code knowledge} data
     * @param outputDirectoryName - the directory name to save exported files to
     * @return true, if the step runned successfully, false otherwise
     */
    public boolean run(MongoDatabase database, Path knowledgePath, String outputDirectoryName) {
        // Retrieves collection names
        List<String> collectionNames = new ArrayList<String>(); 
        database.listCollectionNames().forEach((Consumer<String>) (name -> collectionNames.add(name)));

        Path outputPath = FilesHelper.createDirectories(knowledgePath.resolve(outputDirectoryName));
        if (outputPath == null) {
            return false;
        }

        // Concurrently export collections
        collectionNames.parallelStream().forEach(collectionName -> {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            exportCollection(collection, outputPath);
        });

        return true;
    }

    /**
     * Exports the {@link MongoCollection} with {@code BSON} {@link Document} to 
     * the {@code JSON} file located in the given {@code exportPath}
     * @param collection - the {@link MongoCollection} with {@code BSON} documents
     * @param exportPath - the {@link Path} to export collection to
     */
    private void exportCollection(MongoCollection<Document> collection, Path exportPath) {
        String collectionName = collection.getNamespace().getCollectionName();

        try {
            // Converts BSON collection to JSON array
            List<String> jsonObjects = new ArrayList<String>();
            for (Document document : collection.find()) jsonObjects.add(document.toJson());
            List<String> jsonArray = Arrays.asList("[", String.join(",\n", jsonObjects), "]");

            Path filename = exportPath.resolve(collectionName + ".json");
            Files.write(filename, jsonArray);

            logger.info("The collection \"" + collectionName + "\" was successfully exported");
        } catch (Exception e) {
            logger.severe("Unable to export the collection \"" + collectionName + "\"");
        }
    }

}