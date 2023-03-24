package com.khub.crawling;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.khub.exceptions.InvalidConfigurationException;
import com.khub.misc.HttpRequestBuilder;

/**
 * Confluence API Crawler for retrieving spaces, pages, commentaries and attachments
 */
public class ConfluenceCrawler extends Crawler {

    private static final String URL_KEY = "confluence.url";
    private static final String TOKEN_KEY = "confluence.token";
    private static final String HEADER_KEY = "Cookie";
    private static final String PAYLOAD_KEY = "crowd.token_key";

    private List<JsonElement> groups = new ArrayList<JsonElement>();
    private List<JsonElement> groupMembers = new ArrayList<JsonElement>();
    private List<JsonElement> globalSpaces = new ArrayList<JsonElement>();
    private List<JsonElement> globalPages = new ArrayList<JsonElement>();
    private List<JsonElement> personalSpaces = new ArrayList<JsonElement>();
    private List<JsonElement> personalPages = new ArrayList<JsonElement>();
    private List<JsonElement> commentaries = new ArrayList<JsonElement>();
    private List<JsonElement> attachments = new ArrayList<JsonElement>();

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
     * @return all retrieved data as {@code HashMap} of {@code List} with {@code JsonElements}
     */
    public HashMap<String, List<JsonElement>> run() {
        logger.log(Level.INFO, "Retrieval of Confluence content started");

        retrieveGroups();
        retrieveGroupMembers();
        retrieveGlobalSpaces();
        retrieveGlobalPages();
        retrievePersonalSpaces();
        retrievePersonalPages();
        retrieveCommentariesAndAttachments();

        logger.log(Level.INFO, "Retrieval of Confluence content finished");

        HashMap<String, List<JsonElement>> result = new HashMap<String, List<JsonElement>>();
        result.put("groups", groups);
        result.put("groupMembers", groupMembers);
        result.put("globalSpaces", globalSpaces);
        result.put("globalPages", globalPages);
        result.put("personalSpaces", personalSpaces);
        result.put("personalPages", personalPages);
        result.put("commentaries", commentaries);
        result.put("attachments", attachments);
        return result;
    }

    /**
     * Retrieves all groups from Confluence as {@code JSON}
     */
    private void retrieveGroups() {
        String uri = this.url + "group?limit=9999";

        groups.clear();
        groups.addAll(retrieve(uri));

        if (!groups.isEmpty()) {
            logger.log(Level.INFO, groups.size() + " Confluence groups were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence groups were retrieved");
        }
    }

    /**
     * Retrieves all group members from Confluence as {@code JSON}.
     * Groups must be retrieved prior to this method call.
     */
    private void retrieveGroupMembers() {
        groupMembers.clear();

        groups.parallelStream().forEach(group -> {
            String key = group.getAsJsonObject().get("name").getAsString().replace(" ", "%20");
            String uri = this.url + "group/" + key + "/member?limit=9999";

            List<JsonElement> jsonArray = new ArrayList<>();
            List<JsonElement> result = retrieve(uri);
            for (JsonElement jsonElement : result) {
                JsonObject member = jsonElement.getAsJsonObject();
                member.addProperty("group", key);
                jsonArray.add(member);
            }
            groupMembers.addAll(jsonArray);
        });

        if (!groupMembers.isEmpty()) {
            logger.log(Level.INFO, groupMembers.size() + " Confluence group members were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence group members were retrieved");
        }
    }

    /**
     * Retrieves all global spaces from Confluence as {@code JSON}
     */
    private void retrieveGlobalSpaces() {
        String uri = this.url + "space?type=global&limit=9999";
        globalSpaces.addAll(retrieve(uri));

        if (!globalSpaces.isEmpty()) {
            logger.log(Level.INFO, globalSpaces.size() + " Confluence global spaces were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence global spaces were retrieved");
        }
    }

    /**
     * Retrieves all global pages from Confluence as {@code JSON}.
     * Global spaces must be retrieved prior to this method call.
     */
    private void retrieveGlobalPages() {
        globalPages.clear();

        globalSpaces.parallelStream().forEach(space -> {
            String key = space.getAsJsonObject().get("key").getAsString();
            String uri = this.url + "space/" + key
                + "/content/page?type=page&limit=9999&expand=body.storage,children.comment,children.attachment,"
                + "ancestors,history.contributors.publishers.users,history.lastUpdated";

            globalPages.addAll(retrieve(uri));
        });

        if (!globalPages.isEmpty()) {
            logger.log(Level.INFO, globalPages.size() + " Confluence global pages were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence global pages were retrieved");
        }
    }

    /**
     * Retrieves all personal spaces from Confluence as {@code JSON}
     */
    private void retrievePersonalSpaces() {
        String uri = this.url + "space?type=personal&limit=9999";

        personalSpaces.clear();
        personalSpaces.addAll(retrieve(uri));

        if (!personalSpaces.isEmpty()) {
            logger.log(Level.INFO, personalSpaces.size() + " Confluence personal spaces were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence personal spaces were retrieved");
        }
    }

    /**
     * Retrieves all personal pages from Confluence as {@code JSON}.
     * Personal spaces must be retrieved prior to this method call.
     */
    private void retrievePersonalPages() {
        personalPages.clear();

        personalSpaces.parallelStream().forEach(space -> {
            String key = space.getAsJsonObject().get("key").toString().replace("\"", "");
            String uri = this.url + "space/" + key
                + "/content/page?type=page&limit=9999&expand=body.storage";

            personalPages.addAll(retrieve(uri));
        });

        if (!personalPages.isEmpty()) {
            logger.log(Level.INFO, personalPages.size() + " Confluence personal pages were retrieved");
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

        globalPages.parallelStream().forEach(page -> {
            JsonObject children = page.getAsJsonObject().getAsJsonObject("children");

            int commentariesCount = children.getAsJsonObject("comment").get("size").getAsInt();
            int attachmentCount = children.getAsJsonObject("attachment").get("size").getAsInt();

            if (commentariesCount != 0 || attachmentCount != 0) {
                String key = page.getAsJsonObject().get("id").getAsString();
                String uri = this.url + "content/" + key + "/child/";
    
                if (commentariesCount != 0) {
                    commentaries.addAll(retrieve(uri + "comment?limit=9999&expand=body.storage,ancestors,"
                                                     + "history.contributors.publishers.users,history.lastUpdated"));
                }
    
                if (attachmentCount != 0) {
                    attachments.addAll(retrieve(uri + "attachment?limit=9999"));
                }
            }
        });

        if (!commentaries.isEmpty()) {
            logger.log(Level.INFO, commentaries.size() + " Confluence commentaries were retrieved");
        } else {
            logger.log(Level.WARNING, "No Confluence commentaries were retrieved");
        }

        if (!attachments.isEmpty()) {
            logger.log(Level.INFO, attachments.size() + " Confluence attachments were retrieved");
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
    private List<JsonElement> retrieve(String uri) {
        HttpRequest request = HttpRequestBuilder.build(uri, header);
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
            return jsonObject.getAsJsonArray("results").asList();

        } catch (InterruptedException | IOException e) {
            logger.log(Level.SEVERE, "Unable to send a request and/or receive a response", e);

        } catch (JsonSyntaxException e) {
            logger.log(Level.SEVERE, "Retrieved malformed JSON format", e);
        }

        return null;
    }
}
