package com.khub.crawling;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
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

/**
 * Confluence API Crawler
 */
public class ConfluenceCrawler extends Crawler {

    private static final String URL_KEY = "confluence.url";
    private static final String TOKEN_KEY = "confluence.token";
    private static final String PAYLOAD_KEY = "crowd.token_key";
    private static final String HEADER_KEY = "Cookie";

    private HashSet<JsonElement> globalSpaces = new HashSet<JsonElement>();
    private HashSet<JsonElement> personalSpaces = new HashSet<JsonElement>();
    private HashSet<JsonElement> globalPages = new HashSet<JsonElement>();
    private HashSet<JsonElement> personalPages = new HashSet<JsonElement>();

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

        // TODO: Check URL
        if (isNullOrEmpty(url) || isNullOrEmpty(token)) {
            throw new InvalidConfigurationException("The given URL or token is null or empty");
        }

        return new ConfluenceCrawler(url, header);
    }

    /**
     * Retrieves all global spaces from Confluence as {@code JSON}
     */
    public void retrieveGlobalSpaces() {
        String uri = this.url + "space?type=global&limit=9999";
        JsonArray jsonArray = retrieve(uri);

        logger.log(Level.INFO, "Confluence global spaces retrieved correctly");
        globalSpaces.addAll(jsonArray.asList());
    }

    /**
     * Retrieves all personal spaces from Confluence as {@code JSON}
     */
    public void retrievePersonalSpaces() {
        String uri = this.url + "space?type=personal&limit=9999";
        JsonArray jsonArray = retrieve(uri);

        logger.log(Level.INFO, "Confluence personal spaces retrieved correctly");
        personalSpaces.addAll(jsonArray.asList());
    }

    /**
     * Retrieves all global pages from Confluence as {@code JSON}.
     * Spaces must be retrieved prior to this method call.
     */
    public void retrieveGlobalPages() {
        for (JsonElement space : globalSpaces) {
            String key = space.getAsJsonObject().get("key").toString();
            String uri = this.url + "rest/api/space/" + key
                + "content?type=page&limit=9999&expand=body.storage";

            JsonArray jsonArray = retrieve(uri);
            globalPages.addAll(jsonArray.asList());
        }
        logger.log(Level.INFO, "Confluence global pages retrieved correctly");
    }

    /**
     * Retrieves all personal pages from Confluence as {@code JSON}.
     * Spaces must be retrieved prior to this method call.
     */
    public void retrievePersonalPages() {
        for (JsonElement space : personalSpaces) {
            String key = space.getAsJsonObject().get("key").toString();
            String uri = this.url + "rest/api/space/" + key
                + "content?type=page&limit=9999&expand=body.storage";

            JsonArray jsonArray = retrieve(uri);
            personalPages.addAll(jsonArray.asList());
        }
        logger.log(Level.INFO, "Confluence personal pages retrieved correctly");
    }

    /**
     * TODO: Retrieves all commentaries from Confluence as {@code JSON}
     */
    public void retrieveCommentaries() {
    }

    /**
     * TODO: Retrieves all attachments from Confluence as {@code JSON}
     */
    public void retrieveAttachments() {
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
            return jsonObject.get("results").getAsJsonArray();

        } catch (InterruptedException | IOException e) {
            logger.log(Level.SEVERE, "Unable to send a request and/or receive a response", e);

        } catch (JsonSyntaxException e) {
            logger.log(Level.SEVERE, "Malformed JSON format of Confluence spaces", e);
        }

        return null;
    }

    public HashSet<JsonElement> getGlobalSpaces() {
        return globalSpaces;
    }

    public HashSet<JsonElement> getPersonalSpaces() {
        return personalSpaces;
    }

    public HashSet<JsonElement> getGlobalPages() {
        return globalPages;
    }

    public HashSet<JsonElement> getPersonalPages() {
        return personalPages;
    }
}
