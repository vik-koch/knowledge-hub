package com.khub.processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// JSON Metadata Processor
public class MetadataProcessor {

    // Metadata fields that should be filtered and renamed
    // Keys correspond to new field names after processing
    private static final Map<String, List<String>> mappings = Map.ofEntries(
        Map.entry("_id", Arrays.asList("_id")),
        Map.entry("key", Arrays.asList("key")),
        Map.entry("type", Arrays.asList("type", "messageType")),
        Map.entry("mediaType", Arrays.asList("metadata.mediaType")),
        Map.entry("name", Arrays.asList("name", "displayName")),
        Map.entry("username", Arrays.asList("username")),
        Map.entry("title", Arrays.asList("title, subject")),
        Map.entry("group", Arrays.asList("group")),
        Map.entry("replyToId", Arrays.asList("replyToId")),
        Map.entry("mentions", Arrays.asList("mentions[*].mentionText")),
        Map.entry("link", Arrays.asList("_links.webui", "webUrl")),
        Map.entry("content", Arrays.asList("body.storage.value", "body.content", "description")),
        Map.entry("createdBy", Arrays.asList("history.createdBy.username", "from.user.displayName")),
        Map.entry("createdAt", Arrays.asList("history.createdDate", "createdDateTime")),
        Map.entry("lastUpdatedBy", Arrays.asList("history.lastUpdated.by.username")),
        Map.entry("lastUpdatedAt", Arrays.asList("history.lastUpdated.when", "lastEditedDateTime")),
        Map.entry("contributors", Arrays.asList("history.contributors.publishers.users[*].username")),
        Map.entry("ancestors", Arrays.asList("ancestors[*].id", "teamId", "channelIdentity.channelId"))
    );

    // Prevents instantiation
    private MetadataProcessor() {
    }

    /**
     * Processes a {@code List} of {@code JsonElements} iteratively by filtering
     * the fields and renaming them to the defined mappings
     * @param elements - a {@code List} of {@code JsonElements}
     * @return the processed {@code List} of {@code JsonElements}
     */
    public static List<JsonElement> process(List<JsonElement> elements) {
        if (elements == null) return null;
        List<JsonElement> result = new ArrayList<JsonElement>();

        // Gets all metadata fields from mappings
        List<String> allFields = new ArrayList<String>();
        Map<String, String> presentFields = new HashMap<String, String>();
        mappings.values().forEach(list -> allFields.addAll(list));

        // Checks what fields are present in the first element
        // and puts the found field as key to a new map
        for (String field : allFields) {
            if (getByPath(elements.get(0), field) != null) {
                mappings.entrySet().forEach(entry -> {
                    if (entry.getValue().contains(field)) {
                        presentFields.put(field, entry.getKey());
                    }
                });
            }
        }

        // Creates new JsonElements iteratively by retrieving field values
        for (JsonElement element : elements) {
            JsonObject object = new JsonObject();
            for (String field : presentFields.keySet()) {
                object.add(presentFields.get(field), getByPath(element, field));
            }
            result.add(object);
        }

        return result;
    }

    /**
     * Gets an {@code JsonElement} by a predefined JsonPath. This method
     * supports the retrieval of primitive types, objects and arrays including
     * nested objects. Arrays should be notated as [*] and nested arrays are not supported. <p>
     * Supported Paths: {@code Object1.Object2}, {@code Object1[*]} or {@code Object1[*].Object2} <p>
     * Unsupported Paths {@code Object1[*][*]} or {@code Object1[*].Object2.Object3}
     * @param element - a {@code JsonElement}
     * @param path - a String with JsonPath
     * @return {@code JsonElement} such as primitive, object or array
     */
    public static JsonElement getByPath(JsonElement element, String path){

        String[] keys = path.split("\\.");
        JsonElement result = element;

        for (String key : keys) {
            if (result == null) {
                return null;
            }
            else if (result.isJsonArray()) {
                JsonArray jsonArray = new JsonArray();
                for (JsonElement el : result.getAsJsonArray()) {
                    jsonArray.add(el.getAsJsonObject().get(key));
                }
                result = jsonArray;
            }
            else if (key.contains("[*]")) {
                result = result.getAsJsonObject().getAsJsonArray(key.replace("[*]", ""));
            }
            else if (result.isJsonObject()) {
                result = result.getAsJsonObject().get(key);
            }
        }
        return result;
    }
}
