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

    public final String spaceId = "spaceId";
    public final String pageId = "pageId";

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
        String taskName = "Confluence users";
        logOnTaskStart(taskName);
        
        String groupRequestUrl = endpoint + "rest/api/group?limit=100";
        List<JsonElement> groups = retrieve(groupRequestUrl);

        groups.parallelStream().forEach(group -> {
            try {
                String groupKey = group.getAsJsonObject().get("name").getAsString().replace(" ", "%20");
                String requestUrl = endpoint + "rest/api/group/" + groupKey + "/member?limit=100&expand=personalSpace";

                for (JsonElement jsonElement : retrieve(requestUrl)) {
                    if (jsonElement.getAsJsonObject().has("personalSpace")) {
                        users.add(jsonElement);
                    }
                }
            } catch (Exception e) { }
        });

        logOnTaskFinish(taskName, users);
        return new ArrayList<JsonElement>(users);
    }

    /**
     * Retrieves all {@code Confluence} spaces as {@link JsonElement}s
     * @return the list of {@code Confluence} spaces
     */
    private List<JsonElement> retrieveSpaces() {
        String taskName = "Confluence spaces";
        logOnTaskStart(taskName);

        String requestUrl = endpoint + "rest/api/space?type=global&limit=100";
        List<JsonElement> spaces = retrieve(requestUrl);
        spaces.forEach(element -> {
            JsonObject object = element.getAsJsonObject();
            String spaceHomepageUrl = object.getAsJsonObject("_expandable").get("homepage").getAsString();
            String spaceHomepageId = spaceHomepageUrl.substring(spaceHomepageUrl.lastIndexOf('/') + 1);
            object.remove("id");
            object.addProperty("id", spaceHomepageId);
        });
        
        logOnTaskFinish(taskName, spaces);
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
        String taskName = "Confluence pages";
        logOnTaskStart(taskName);

        spaces.parallelStream().forEach(space -> {
            if (space.isJsonObject()) {
                JsonObject spaceObject = space.getAsJsonObject();
                try {
                    String spaceKey = spaceObject.get("key").getAsString();                
                    String spaceId = spaceObject.get("id").getAsString();
                    String requestUrl = endpoint + "rest/api/space/" + spaceKey + "/content/page?type=page&limit=20"
                        + "&expand=body.view,children.comment,ancestors,history.lastUpdated";

                    List<JsonElement> result = retrieve(requestUrl);
                    for (JsonElement element : result) {
                        JsonObject object = element.getAsJsonObject();

                        // Remove the top level space page with aggregated content
                        if (object.get("id").getAsString().equals(spaceId)) {
                            continue;
                        }

                        JsonArray ancestors = object.get("ancestors").getAsJsonArray();

                        // Inject the correct first ancestor
                        String ancestor = ancestors.size() < 1
                            ? "" 
                            : ancestors.get(ancestors.size() - 1).getAsJsonObject().get("id").getAsString();

                            object.addProperty("ancestor", ancestor);

                        object.addProperty(this.spaceId, spaceId);
                        pages.add(element);
                    }
                    logOnSuccess(taskName, spaces, space, result);

                } catch (Exception e) {
                    logger.warning("Unable to crawl " + taskName + " for space id " + spaceObject.get("id"));
                }
            }
        });

        logOnTaskFinish(taskName, pages);
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
        String taskName = "Confluence comments";
        logOnTaskStart(taskName);

        // Extract pages with comments
        List<JsonElement> pagesWithComments = new ArrayList<JsonElement>();
        for (JsonElement page : pages) {
            if (page.isJsonObject()) {
                JsonObject pageObject = page.getAsJsonObject();
                JsonObject children = pageObject.getAsJsonObject("children");
                if (children == null) {
                    continue;
                }

                int commentsCount = children.getAsJsonObject("comment").get("size").getAsInt();
                if (commentsCount > 0) {
                    pagesWithComments.add(page);
                }
            }
        }

        logger.info("Only " + pagesWithComments.size() + " from " + pages.size() + " pages have comments");

        pagesWithComments.parallelStream().forEach(page -> {
            if (page.isJsonObject()) {
                JsonObject pageObject = page.getAsJsonObject();
                try {
                    String pageKey = pageObject.get("id").getAsString();
                    String requestUrl = endpoint + "rest/api/content/" + pageKey + "/child/comment?limit=100"
                        + "&expand=body.view,ancestors,history.lastUpdated";

                    String spaceId = pageObject.get(this.spaceId).getAsString();

                    List<JsonElement> result = retrieve(requestUrl);
                    for (JsonElement element : result) {
                        JsonObject object = element.getAsJsonObject();

                        // Inject the correct first ancestor
                        object.addProperty(this.pageId, pageKey);
                        object.addProperty(this.spaceId, spaceId);
                        comments.add(element);
                    }
                    logOnSuccess(taskName, pagesWithComments, page, result);

                } catch (Exception e) {
                    logger.warning("Unable to crawl" + taskName + " for page id " + pageObject.get("id"));
                }
            }
        });

        logOnTaskFinish(taskName, comments);
        return comments;
    }

    /**
     * Retrieves data from {@code Confluence} API by for the given request
     * and returns an empty collection if request failed or no data received
     * @param requestUrl - the request {@code URL}
     * @return the response
     */
    private List<JsonElement> retrieve(String requestUrl) {
        JsonArray jsonArray = new JsonArray();
        try {
            do {
                HttpRequest request = HttpRequestBuilder.build(requestUrl, requestHeader.toNameValuePair());
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                JsonObject jsonObject = JsonParser.parseString(response.body().trim()).getAsJsonObject();

                jsonArray.addAll(jsonObject.getAsJsonArray("results"));

                JsonElement nextRequestUrl = jsonObject.getAsJsonObject("_links").get("next");
                requestUrl = nextRequestUrl != null ? endpoint + nextRequestUrl.getAsString() : null;
            } while (requestUrl != null);

            return jsonArray.asList();

        } catch (InterruptedException | IllegalArgumentException | IOException  | SecurityException  e) {
            logger.warning("Unable to send a request and/or receive a response for request \"" + requestUrl + "\"");

        } catch (NullPointerException | JsonParseException | ClassCastException | IllegalStateException  e) {
            logger.warning("Retrieved malformed JSON format for request \"" + requestUrl + "\"");

        } catch (URISyntaxException e) {
            // Do nothing
        }

        return List.of();
    }

}