package com.khub.crawling;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.khub.common.AuthenticationHeader;
import com.khub.common.HttpRequestBuilder;

public class ConfluenceCrawler extends AbstractCrawler {

    public ConfluenceCrawler(URL confluenceEndpoint, AuthenticationHeader requestHeader) {
        super(confluenceEndpoint, requestHeader);
    }

    /**
     * Starts the {@link ConfluenceCrawler} and returns retrieved data as
     * Lists of {@link JsonElement}s mapped to the corresponding data labels.
     * {@code Confluence} data includes users, spaces, pages and comments
     * @return the retrieved {@code Confluence} data
     */
    public Map<String, List<JsonElement>> run() {

        List<JsonElement> users = retrieveUsers();
        List<JsonElement> spaces = retrieveSpaces();
        List<JsonElement> pages = retrievePages(spaces);
        List<JsonElement> comments = retrieveComments(pages);

        return Map.of("users", users, "spaces", spaces, "pages", pages, "comments", comments);
    }

    /**
     * Retrieves all {@code Confluence} users as {@link JsonElement}s
     * @return the list of {@code Confluence} users
     */
    private List<JsonElement> retrieveUsers() {
        Set<JsonElement> users = new HashSet<JsonElement>();

        String groupRequestUrl = endpoint + "group?limit=9999";
        List<JsonElement> groups = retrieve(groupRequestUrl);

        groups.parallelStream().forEach(group -> {
            try {
                String groupKey = group.getAsJsonObject().get("name").getAsString().replace(" ", "%20");
                String requestUrl = endpoint + "group/" + groupKey + "/member?limit=9999&expand=personalSpace";

                for (JsonElement jsonElement : retrieve(requestUrl)) {
                    if (jsonElement.getAsJsonObject().has("personalSpace")) {
                        users.add(jsonElement);
                    }
                }
            } catch (Exception e) { }
        });

        if (!users.isEmpty()) {
            logger.info(users.size() + " Confluence users were retrieved");
        } else {
            logger.warning("No Confluence users were retrieved");
        }

        return new ArrayList<JsonElement>(users);
    }

    /**
     * Retrieves all {@code Confluence} spaces as {@link JsonElement}s
     * @return the list of {@code Confluence} spaces
     */
    private List<JsonElement> retrieveSpaces() {

        String requestUrl = this.endpoint + "space?type=global&limit=9999";
        List<JsonElement> spaces = retrieve(requestUrl);

        if (!spaces.isEmpty()) {
            logger.info(spaces.size() + " Confluence spaces were retrieved");
        } else {
            logger.warning("No Confluence spaces were retrieved");
        }

        return spaces;
    }

    /**
     * Retrieves all {@code Confluence} pages as {@link JsonElement}s
     * for the given list of {@code Confluence} spaces
     * @param spaces - the list of {@code Confluence} spaces
     * @return the list of {@code Confluence} pages
     */
    private List<JsonElement> retrievePages(List<JsonElement> spaces) {
        List<JsonElement> pages = new ArrayList<JsonElement>();

        spaces.parallelStream().forEach(space -> {
            try {
                String spaceKey = space.getAsJsonObject().get("key").getAsString();
                String requestUrl = endpoint + "space/" + spaceKey + "/content/page?type=page&limit=9999"
                    + "&expand=body.view,children.comment,ancestors,space,history";
                pages.addAll(retrieve(requestUrl));

            } catch (Exception e) { }     
        });

        if (!pages.isEmpty()) {
            logger.info(pages.size() + " Confluence pages were retrieved");
        } else {
            logger.warning("No Confluence pages were retrieved");
        }

        return pages;
    }

    /**
     * Retrieves all {@code Confluence} comments as {@link JsonElement}s
     * for the given list of {@code Confluence} pages
     * @param pages - the list of {@code Confluence} pages
     * @return the list of {@code Confluence} comments
     */
    private List<JsonElement> retrieveComments(List<JsonElement> pages) {
        List<JsonElement> comments = new ArrayList<JsonElement>();

        pages.parallelStream().forEach(page -> {
            try {
                JsonObject children = page.getAsJsonObject().getAsJsonObject("children");
                int commentsCount = children.getAsJsonObject("comment").get("size").getAsInt();
    
                if (commentsCount != 0) {
                    String pageKey = page.getAsJsonObject().get("id").getAsString();
                    String requestUrl = endpoint + "content/" + pageKey + "/child/comment?limit=9999"
                        + "&expand=body.view,ancestors,history";
                    List<JsonElement> result = retrieve(requestUrl);
                    result.forEach(element -> element.getAsJsonObject().addProperty("ancestor", pageKey));
                    comments.addAll(result);
                }
            } catch (Exception e) { } 
        });

        if (!comments.isEmpty()) {
            logger.info(comments.size() + " Confluence comments were retrieved");
        } else {
            logger.warning("No Confluence comments were retrieved");
        }

        return comments;
    }

    /**
     * Retrieves data from {@code Confluence} API by for the given request
     * and returns an empty collection if request failed or no data received
     * @param requestUrl - the request {@code URL}
     * @return the response
     */
    private List<JsonElement> retrieve(String requestUrl) {
        try {
            HttpRequest request = HttpRequestBuilder.build(requestUrl, requestHeader.toNameValuePair());
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray jsonArray = jsonObject.getAsJsonArray("results");
            if (jsonArray != null) {
                return jsonArray.asList();
            }

        } catch (InterruptedException | IllegalArgumentException | IOException  | SecurityException  e) {
            logger.severe("Unable to send a request and/or receive a response for request \"" + requestUrl + "\"");

        } catch (NullPointerException | JsonParseException | ClassCastException | IllegalStateException  e) {
            logger.severe("Retrieved malformed JSON format for request \"" + requestUrl + "\"");
                
        } catch (URISyntaxException e) {
            // Do nothing
        }

        return List.of();
    }

}