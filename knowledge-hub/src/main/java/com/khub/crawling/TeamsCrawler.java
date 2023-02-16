package com.khub.crawling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import java.io.IOException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class TeamsCrawler extends AbstractCrawler {

    public TeamsCrawler(URL teamsEndpoint, AuthenticationHeader requestHeader) {
        super(teamsEndpoint, requestHeader);
    }

    /**
     * Starts the {@link TeamsCrawler} and returns retrieved data as
     * Lists of {@link JsonElement}s mapped to the corresponding data labels.
     * {@code Teams} data includes teams, channels and posts
     * @return the retrieved {@code Teams} data
     */
    public Map<String, List<JsonElement>> run() {

        List<JsonElement> teams = retrieveTeams();
        List<JsonElement> channels = retrieveChannels(teams);
        List<JsonElement> posts = retrievePosts(channels);

        return Map.of("teams", teams, "channels", channels, "posts", posts);
    }

    /**
     * Retrieves all {@code Teams} teams joined by the request as {@link JsonElement}s
     * @return the list of {@code Teams} teams
     */
    private List<JsonElement> retrieveTeams() {

        String requestUrl = this.endpoint + "v1.0/me/joinedTeams";
        List<JsonElement> teams = retrieve(requestUrl);

        if (!teams.isEmpty()) {
            logger.log(Level.INFO, teams.size() + " Teams teams were retrieved");
        } else {
            logger.log(Level.WARNING, "No Teams teams were retrieved");
        }

        return teams;
    }

    /**
     * Retrieves all {@code Teams} channels as {@link JsonElement}s
     * for the given list of {@code Teams} teams
     * @param teams - the list of {@code Teams} teams
     * @return the list of {@code Teams} channels
     */
    private List<JsonElement> retrieveChannels(List<JsonElement> teams) {
        List<JsonElement> channels = new ArrayList<JsonElement>();

        teams.parallelStream().forEach(team -> {
            try {
                String teamKey = team.getAsJsonObject().get("id").getAsString();
                String requestUrl = this.endpoint + "v1.0/teams/" + teamKey + "/channels";

                for (JsonElement channel : retrieve(requestUrl)) {
                    // Enrich channel data by team identity
                    channel.getAsJsonObject().addProperty("ancestor", teamKey);
                    channels.add(channel);
                }
            } catch (Exception e) { }
        });

        if (!channels.isEmpty()) {
            logger.log(Level.INFO, channels.size() + " Teams channels were retrieved");
        } else {
            logger.log(Level.WARNING, "No Teams channels were retrieved");
        }

        return channels;
    }

    /**
     * Retrieves all {@code Teams} posts as {@link JsonElement}s
     * for the given list of {@code Teams} channels
     * @param channels - the list of {@code Teams} channels
     * @return the list of {@code Teams} posts
     */
    private List<JsonElement> retrievePosts(List<JsonElement> channels) {
        List<JsonElement> posts = new ArrayList<JsonElement>();

        channels.parallelStream().forEach(channel -> {
            try {
                String teamKey = channel.getAsJsonObject().get("ancestor").getAsString();
                String channelKey = channel.getAsJsonObject().get("id").getAsString();
                String requestUrl = this.endpoint + "v1.0/teams/" + teamKey + "/channels/" 
                    + channelKey + "/messages?top=100";

                for (JsonElement post : retrieve(requestUrl)) {
                    if (post.getAsJsonObject().get("messageType").getAsString().equals("message")) {
                        posts.add(post);
                    }
                }
            } catch (Exception e) { }
        });

        if (!posts.isEmpty()) {
            logger.log(Level.INFO, posts.size() + " Teams posts were retrieved");
        } else {
            logger.log(Level.WARNING, "No Teams posts were retrieved");
        }

        return posts;
    }

    /**
     * Retrieves data from {@code Teams} API by for the given request
     * and returns an empty collection if request failed or no data received.
     * The number of responses per request is limited to 20 and each response
     * contains {@code @odata.nextLink} with the next response portion
     * @param requestUrl - the request {@code URL}
     * @return the response
     */
    private List<JsonElement> retrieve(String requestUrl) {
        JsonArray jsonArray = new JsonArray();
        try {
            do {
                HttpRequest request = HttpRequestBuilder.build(requestUrl, requestHeader.toNameValuePair());
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

                JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();

                if (jsonObject.get("value") != null) {
                    JsonArray values = jsonObject.getAsJsonArray("value");
                    jsonArray.addAll(values);
                }
                else {
                    jsonArray.add(jsonObject);
                }

                JsonElement nextRequestUrl = jsonObject.get("@odata.nextLink");
                requestUrl = nextRequestUrl != null ? nextRequestUrl.getAsString() : null;
            } while (requestUrl != null);

            return jsonArray.asList();

        } catch (InterruptedException | IllegalArgumentException | IOException  | SecurityException  e) {
            logger.log(Level.SEVERE, "Unable to send a request and/or receive a response for request \"" + requestUrl + "\"");

        } catch (NullPointerException | JsonParseException | ClassCastException | IllegalStateException  e) {
            logger.log(Level.SEVERE, "Retrieved malformed JSON format for request \"" + requestUrl + "\"");
        }

        return List.of();
    }

}