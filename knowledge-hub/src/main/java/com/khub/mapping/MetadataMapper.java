package com.khub.mapping;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.Document;

import com.khub.exceptions.FailedPipelineStepException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MetadataMapper {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Starts {@link MetadataMapper} for {@code JSON}-to-{@code RML} mapping of retrieved
     * metadata fields using the defined {@code RML} mappings provided in the {@code metadataPath}
     * and saves the mapped files to the output folder under the same {@link Path}
     * @param database - the {@link MongoDatabase} to read data from
     * @param metadataPath - the {@link Path} with {@code RML} mappings
     * @param dockerPath - the {@link Path} to {@code docker-compose.yml}
     */
    public void run(MongoDatabase database, Path metadataPath, Path dockerPath) {
        String sourceDirectoryName = "source";
        String outputDirectoryName = "output";

        try {
            // Retrieves collection names
            List<String> collectionNames = new ArrayList<String>(); 
            database.listCollectionNames().forEach((Consumer<String>) (name -> collectionNames.add(name)));

            Path sourcePath = Files.createDirectories(Paths.get(metadataPath + File.separator + sourceDirectoryName));

            // Concurrently export collections
            collectionNames.parallelStream().forEach(collectionName -> {
                MongoCollection<Document> collection = database.getCollection(collectionName);
                exportCollection(collection, sourcePath);
            });

            // Retrieves mapping filenames
            List<String> filenames = Stream.of(new File(metadataPath.toString()).listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toList());

            Files.createDirectories(Paths.get(metadataPath + File.separator + outputDirectoryName));
            RMLMapper mapper = new RMLMapper(metadataPath, dockerPath);
            filenames.parallelStream().forEach(filename -> {
                mapper.execute(filename, outputDirectoryName);
            });

        } catch (Exception e) {
            logger.severe("Unable to create output folders under \"" + metadataPath + "\"");
            throw new FailedPipelineStepException();
        }
    }

    /**
     * Exports the {@link MongoCollection} with {@code BSON} {@link Document} to 
     * the {@code JSON} file located in the given {@code exportPath}
     * @param collection - the {@link MongoCollection} with {@code BSON} documents
     * @param exportPath - the {@link Path} to export collection to
     */
    private void exportCollection(MongoCollection<Document> collection, Path exportPath) {
        String collectionName = collection.getNamespace().getCollectionName();
        logger.info("Starting to export the collection \"" + collectionName + "\"");

        try {
            // Converts BSON collection to JSON array
            List<String> jsonObjects = new ArrayList<String>();
            for (Document document : collection.find()) jsonObjects.add(document.toJson());
            List<String> jsonArray = Arrays.asList("[", String.join(",\n", jsonObjects), "]");

            Path filename = Paths.get(exportPath + File.separator + collectionName + ".json");
            Files.write(filename, jsonArray);

            logger.info("The collection \"" + collectionName + "\" was successfully exported");
        } catch (Exception e) {
            logger.severe("Unable to export the collection \"" + collectionName + "\"");
        }
    }

}