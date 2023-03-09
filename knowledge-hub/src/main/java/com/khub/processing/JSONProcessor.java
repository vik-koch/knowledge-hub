package com.khub.processing;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bson.Document;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class JSONProcessor {

    private static final Logger logger = Logger.getLogger(JSONProcessor.class.getName());

    // Field jsonPaths that should be filtered and renamed
    private final Map<String, List<String>> mappings;

    // Confluence links are retrieved as short form w/o domain name
    private final String confluenceBaseUrl;

    // Count of possible exceptions during processing
    private int exceptionCount = 0;

    private JSONProcessor(Map<String, List<String>> mappings, String confluenceBaseUrl) {
        this.mappings = mappings;
        this.confluenceBaseUrl = confluenceBaseUrl;
    }

    /**
     * Returns an instance of {@link JSONProcessor} if the {@code mapping} file was
     * successfully read from the given {@code processingPath}, null otherwise
     * @param processingPath - the {@link Path} to the {@code JSON} mapping file
     * @param confluenceEndpoint - the {@code Confluence} URL for extending links
     * @return the {@link JSONProcessor}
     */
    public static JSONProcessor of(Path processingPath, URL confluenceEndpoint) {
        try {
            String jsonString = Files.readString(processingPath);
            Type mapType = new TypeToken<HashMap<String, List<String>>>() {}.getType();
            Map<String, List<String>> mappings = new Gson().fromJson(jsonString, mapType);

            String confluenceBaseUrl = confluenceEndpoint != null 
                ? confluenceEndpoint.getProtocol() + "://" + confluenceEndpoint.getHost() + "/wiki"
                : "";
            return new JSONProcessor(mappings, confluenceBaseUrl);

        } catch (IOException | SecurityException e) {
            logger.severe("Unable to read the provided mapping file at \"" + processingPath + "\"");

        } catch (JsonSyntaxException e) {
            logger.severe("The provided mapping file at \"" + processingPath + "\" has invalid syntax");
        }

        return null;
    }

    /**
     * Starts the {@link JSONProcessor} for processing retrieved fields from all collections in 
     * {@code sourceDatabase} and writes the processed {@code JSON} output to {@code outputDatabase}.
     * Processing extracts and unifies fields as defined in the {@code JSON} mapping.
     * @param sourceDatabase - the {@link MongoDatabase} to read data from
     * @param outputDatabase - the {@link MongoDatabase} to write data to
     * @return true if the step runned successfully, false otherwise
     */
    public boolean run(MongoDatabase sourceDatabase, MongoDatabase outputDatabase) {

        // Retrieves collection names from source database
        List<String> collectionNames = new ArrayList<String>(); 
        for (String collectionName : sourceDatabase.listCollectionNames()) {
            collectionNames.add(collectionName);
        }

        // Processes field jsonPaths for each collection
        collectionNames.forEach(collectionName -> {
            try {
                MongoCollection<Document> sourceCollection = sourceDatabase.getCollection(collectionName);
                MongoCollection<Document> outputCollection = outputDatabase.getCollection(collectionName);

                List<JsonElement> source = convertToJsonElements(sourceCollection);
                List<JsonElement> data = process(source);
        
                List<Document> output = convertToBsonDocuments(data);
                outputCollection.insertMany(output);

                logger.info("Processed data for collection \"" + collectionName + "\"");

            } catch (IllegalArgumentException | MongoException e) {
                exceptionCount++;
                logger.severe("Unable to process data for collection \"" + collectionName + "\"");
            }
        });

        return !(collectionNames.size() == exceptionCount);
    }

    /**
     * Iteratively processes the {@link List} of {@link JsonElement}s 
     * by extracting values with the predefined {@code JsonPath}-like 
     * {@link String}s and renaming them 
     * @param source - the {@link List} of {@List JsonElement}s
     * @return the processed {@link List} of {@List JsonElement}s
     */
    private List<JsonElement> process(List<JsonElement> source) {
        List<JsonElement> output = new ArrayList<JsonElement>();

        if (source.size() < 1) {
            return output;
        }

        // All json paths present in the first elements
        Map<String, String> jsonPaths = checkFields(source.get(0));

        // Creates new JsonElements iteratively by retrieving jsonPath values
        for (JsonElement item : source) {
            JsonObject object = new JsonObject();
            jsonPaths.keySet().forEach(jsonPath -> {
                JsonElement result = getByPath(item, jsonPath);

                // Adds Confluence domain name to links
                if (result != null && jsonPath.contains("_links.webui")) {
                    result = new JsonPrimitive(confluenceBaseUrl + result.getAsJsonPrimitive().getAsString());
                }

                // Combines result to array if another result
                // was found for the same json path
                String member = jsonPaths.get(jsonPath);
                if (object.has(member)) {
                    JsonElement element = object.get(member);
                    if (!element.isJsonArray()) {
                        JsonArray array = new JsonArray();
                        array.add(element);
                        element = array;
                        object.remove(member);
                        object.add(member, element);
                    };
                    if (result.isJsonObject()) {
                        element.getAsJsonArray().add(result);
                    }
                    if (result.isJsonArray()) {
                        element.getAsJsonArray().addAll(result.getAsJsonArray());
                    }
                } else {
                    object.add(member, result);
                }
            });

            output.add(object);
        }

        return output;
    }

    /**
     * Converts the given {@link MongoCollection} of {@code BSON}
     * {@link Document}s to the {@link List} of {@link JsonElement}s
     * @param documents - the {@link Document}s to convert to {@link JsonElement}s
     * @return the {@link List} with {@code JSON} data
     */
    private List<JsonElement> convertToJsonElements(MongoCollection<Document> documents) {
        List<JsonElement> elements = new ArrayList<JsonElement>();

        for (Document document : documents.find()) {
            elements.add(JsonParser.parseString(document.toJson()));
        }

        return elements;
    }

    /**
     * Converts the given {@link List} of {@link JsonElement}s to
     * the {@link List} of {@code BSON} {@link Document}s 
     * @param elements - the {@link JsonElement}s to convert to {@link Document}s
     * @return the {@link List} with {@code BSON} data
     */
    private List<Document> convertToBsonDocuments(List<JsonElement> elements) {
        List<Document> documents = new ArrayList<Document>();

        for (JsonElement element : elements) {
            documents.add(Document.parse(element.toString()));
        }

        return documents;
    }

    /**
     * Checks what {@code JsonPath}s from {@code mappings} are present
     * in the given sample and returns a new swapped {@link Map} of fields
     * with {@code JsonPath} as key and new field name as value
     * @param sample - the item of a {@link JsonElement} collection
     * @return the new {@link Map} with {@code JsonPath}s present in the sample
     */
    private Map<String, String> checkFields(JsonElement sample) {

        // Fields map with jsonPath as key and new field name as value
        Map<String, String> fields = new HashMap<String, String>();

        // Checks what jsonPaths are present in the sample
        // and puts the found jsonPath as key to the new map
        for (Map.Entry<String, List<String>> mapping : mappings.entrySet()) {
            for (String jsonPath : mapping.getValue()) {
                if (getByPath(sample, jsonPath) != null) {
                    fields.put(jsonPath, mapping.getKey());
                }
            }
        }

        return fields;
    }

    /**
     * Extracts the value of a {@link JsonElement} by the predefined {@code JsonPath}-like {@link String}. 
     * This method supports the retrieval of primitive types, objects and arrays including
     * nested objects. Arrays should be notated as [*] and nested arrays are not supported. <p>
     * Supported Paths: {@code Object1.Object2}, {@code Object1[*]} or {@code Object1[*].Object2} <p>
     * Unsupported Paths: {@code Object1[*][*]} or {@code Object1[*].Object2.Object3}
     * @param item - the parent {@link JsonElement}
     * @param jsonPath - the {@code JsonPath} to retrieve the element value
     * @return {@code JsonElement} such as primitive, object or array
     */
    private JsonElement getByPath(JsonElement item, String jsonPath) {

        String[] keys = jsonPath.split("\\.");
        JsonElement result = item;

        for (String key : keys) {
            try {
                if (result == null) {
                    return null;
                }
                else if (result.isJsonArray()) {
                    JsonArray jsonArray = new JsonArray();
                    for (JsonElement element : result.getAsJsonArray()) {
                        jsonArray.add(element.getAsJsonObject().get(key));
                    }
                    result = jsonArray;
                }
                else if (key.contains("[*]")) {
                    String member = key.replace("[*]", "");
                    result = result.getAsJsonObject().getAsJsonArray(member);
                }
                else if (result.isJsonObject()) {
                    result = result.getAsJsonObject().get(key);
                }
            } catch (IllegalStateException | ClassCastException | NullPointerException e) {
                logger.warning("Cannot retrieve value for the jsonPath \"" + jsonPath + "\"");
            }
        }
        return result;
    }

}