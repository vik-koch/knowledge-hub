package com.khub.crawling;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.logging.Level;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.khub.exceptions.InvalidConfigurationException;
import com.khub.misc.HttpRequestBuilder;

// TODO: Maybe make requests parallelizable
// (one space => all pages for space => all comments/attachments for each page)

/**
 * Confluence API Crawler for retrieving spaces, pages, commentaries and attachments
 */
public class ConfluenceCrawler extends Crawler {

    private static final String URL_KEY = "confluence.url";
    private static final String TOKEN_KEY = "confluence.token";
    private static final String HEADER_KEY = "Cookie";
    private static final String PAYLOAD_KEY = "crowd.token_key";

    private HashSet<JsonElement> globalSpaces = new HashSet<JsonElement>();
    private HashSet<JsonElement> globalPages = new HashSet<JsonElement>();
    private HashSet<JsonElement> personalSpaces = new HashSet<JsonElement>();
    private HashSet<JsonElement> personalPages = new HashSet<JsonElement>();
    private HashSet<JsonElement> commentaries = new HashSet<JsonElement>();
    private HashSet<JsonElement> attachments = new HashSet<JsonElement>();

    // Constructor
    private ConfluenceCrawler(String url, String[] header) {
        super(url, header);
    }

    /**
     * Instantiates a {@code ConfluenceCrawler} if provided
     * configuration is plausible, throws otherwise
     * @param configuration - {@code Properties} object
     * @return a {@code ConfluenceCrawler} instance
     * @throws InvalidConfigurationException - if configuration is false
     */
    public static ConfluenceCrawler of(Properties configuration) throws InvalidConfigurationException {
        String url = configuration.getProperty(URL_KEY);
        String token = configuration.getProperty(TOKEN_KEY);
        String[] header = new String[] {HEADER_KEY, PAYLOAD_KEY + '=' + token};

        // TODO: Check URL, alternatively also:
        // 1. Ping the URL to check the connection
        // 2. Send simple GET request to check the token correctness
        if (isNullOrEmpty(url) || isNullOrEmpty(token)) {
            throw new InvalidConfigurationException("The given URL or token is null or empty");
        }

        return new ConfluenceCrawler(url, header);
    }

    /**
     * Runs the retrieval process from Confluence that cascades over global spaces, pages,
     * commentaries and attachments, as well as over private spaces and pages.
     * @return all retrieved data as {@code HashMap} of {@code HashSets} with {@code JsonElements}
     */
    public HashMap<String, HashSet<JsonElement>> run() {
        logger.log(Level.INFO, "Retrieval of Confluence content started");

        retrieveGlobalSpaces();
        retrieveGlobalPages();
        retrievePersonalSpaces();
        retrievePersonalPages();
        retrieveCommentariesAndAttachments();

        logger.log(Level.INFO, "Retrieval of Confluence content finished");
        logger.log(Level.INFO, "Retrieved " + globalSpaces.size() + " global spaces, " + globalPages.size() 
            + " global pages with " + commentaries.size() + " commentaries and " + attachments.size()
            + " attachments, as well as " + personalSpaces.size() + " personal spaces and " + personalPages.size()
            + " personal pages");

        return new HashMap<String, HashSet<JsonElement>>() {{
            put("globalSpaces", globalSpaces);
            put("globalPages", globalPages);
            put("personalSpaces", personalSpaces);
            put("personalPages", personalPages);
            put("commentaries", commentaries);
            put("attachments", attachments);
        }};
    }

    /**
     * Retrieves all global spaces from Confluence as {@code JSON}
     */
    private void retrieveGlobalSpaces() {
        String uri = this.url + "space?type=global&limit=9999";
        JsonArray jsonArray = retrieve(uri);

        globalSpaces.clear();
        globalSpaces.addAll(jsonArray.asList());

        if (!globalSpaces.isEmpty()) {
            logger.log(Level.INFO, "Confluence global spaces were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence global spaces were retrieved");
        }
    }

    /**
     * Retrieves all global pages from Confluence as {@code JSON}.
     * Spaces must be retrieved prior to this method call.
     */
    private void retrieveGlobalPages() {
        globalPages.clear();

        for (JsonElement space : globalSpaces) {
            String key = space.getAsJsonObject().get("key").getAsString();
            String uri = this.url + "space/" + key
                + "/content/page?type=page&limit=9999&expand=body.storage,children.comment,children.attachment";

            JsonArray jsonArray = retrieve(uri);
            globalPages.addAll(jsonArray.asList());
        }

        if (!globalPages.isEmpty()) {
            logger.log(Level.INFO, "Confluence global pages were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence global pages were retrieved");
        }
    }

    /**
     * Retrieves all personal spaces from Confluence as {@code JSON}
     */
    private void retrievePersonalSpaces() {
        String uri = this.url + "space?type=personal&limit=9999";
        JsonArray jsonArray = retrieve(uri);

        personalSpaces.clear();
        personalSpaces.addAll(jsonArray.asList());

        if (!personalSpaces.isEmpty()) {
            logger.log(Level.INFO, "Confluence personal spaces were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence personal spaces were retrieved");
        }
    }

    /**
     * Retrieves all personal pages from Confluence as {@code JSON}.
     * Spaces must be retrieved prior to this method call.
     */
    private void retrievePersonalPages() {
        personalPages.clear();

        for (JsonElement space : personalSpaces) {
            String key = space.getAsJsonObject().get("key").toString().replace("\"", "");
            String uri = this.url + "space/" + key
                + "/content/page?type=page&limit=9999&expand=body.storage";

            JsonArray jsonArray = retrieve(uri);
            personalPages.addAll(jsonArray.asList());
        }
        if (!personalPages.isEmpty()) {
            logger.log(Level.INFO, "Confluence personal pages were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence personal pages were retrieved");
        }
    }

    /**
     * Retrieves all commentaries and attachments from Confluence global pages
     * as {@code JSON}. Global pages must be retrieved prior to this method call.
     */
    private void retrieveCommentariesAndAttachments() {
        commentaries.clear();
        attachments.clear();

        for (JsonElement page : globalPages) {
            JsonObject children = page.getAsJsonObject().getAsJsonObject("children");

            int commentariesCount = children.getAsJsonObject("comment").get("size").getAsInt();
            int attachmentCount = children.getAsJsonObject("comment").get("size").getAsInt();

            if (commentariesCount == 0 && attachmentCount == 0) {
                continue;
            }

            String key = page.getAsJsonObject().get("id").getAsString();
            String uri = this.url + "content/" + key + "/child/";

            if (commentariesCount != 0) {
                JsonArray jsonArray = retrieve(uri + "comment?limit=9999&expand=body.storage");
                commentaries.addAll(jsonArray.asList());
            }

            if (attachmentCount != 0) {
                JsonArray jsonArray = retrieve(uri + "attachment?limit=9999");
                attachments.addAll(jsonArray.asList());
            }
        }

        if (!commentaries.isEmpty()) {
            logger.log(Level.INFO, "Confluence commentaries were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence commentaries were retrieved");
        }

        if (!attachments.isEmpty()) {
            logger.log(Level.INFO, "Confluence attachments were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence attachments were retrieved");
        }
    }

    /**
     * Common method for retrieving content from
     * Confluence using the given API request
     * @param uri - specific request {@code URI}
     * @return response as {@code JsonArray} or null if failed
     */
    private JsonArray retrieve(String uri) {
        HttpRequest request = HttpRequestBuilder.build(uri, header);
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
            return jsonObject.getAsJsonArray("results");

        } catch (InterruptedException | IOException e) {
            logger.log(Level.SEVERE, "Unable to send a request and/or receive a response", e);

        } catch (JsonSyntaxException e) {
            logger.log(Level.SEVERE, "Retrieved malformed JSON format", e);
        }

        return null;
    }
}