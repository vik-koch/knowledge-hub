package com.khub.crawling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.khub.common.AuthenticationHeader;
import com.khub.common.HttpRequestBuilder;

public class TeamsCrawler extends AbstractCrawler {

    public final String teamId = "teamId";

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
        String taskName = "MS Teams teams";
        logOnTaskStart(taskName);

        String requestUrl = this.endpoint + "v1.0/me/joinedTeams";
        List<JsonElement> teams = retrieve(requestUrl);

        logOnTaskFinish(taskName, teams);
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
        String taskName = "MS Teams channels";
        logOnTaskStart(taskName);

        teams.parallelStream().forEach(team -> {
            if (team.isJsonObject()) {
                JsonObject teamObject = team.getAsJsonObject();
                try {
                    String teamKey = teamObject.get("id").getAsString();
                    String requestUrl = this.endpoint + "v1.0/teams/" + teamKey + "/channels";

                    List<JsonElement> result = retrieve(requestUrl);
                    for (JsonElement channel : result) {
                        // Inject the correct first ancestor
                        channel.getAsJsonObject().addProperty(this.teamId, teamKey);
                        channels.add(channel);
                    }
                    logOnSuccess(taskName, teams, team, result);

                } catch (Exception e) {
                    logger.warning("Unable to crawl " + taskName + " for team id \"" + teamObject.get("id") + "\"");
                }
            }
        });

        logOnTaskFinish(taskName, channels);
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
        String taskName = "MS Teams posts";
        logOnTaskStart(taskName);

        channels.parallelStream().forEach(channel -> {
            if (channel.isJsonObject()) {
                JsonObject channelObject = channel.getAsJsonObject();
                try {
                    String teamKey = channelObject.get(this.teamId).getAsString();
                    String channelKey = channelObject.get("id").getAsString();
                    String requestUrl = this.endpoint + "v1.0/teams/" + teamKey + "/channels/" 
                        + channelKey + "/messages?top=100";

                    List<JsonElement> result = retrieve(requestUrl);
                    for (JsonElement post : result) {
                        if (post.getAsJsonObject().get("messageType").getAsString().equals("message")) {
                            posts.add(post);
                        }
                    }
                    logOnSuccess(taskName, channels, channel, result);

                } catch (Exception e) {
                    logger.warning("Unable to crawl " + taskName + " for channel id " + channelObject.get("id") + " and team id " + channelObject.get(this.teamId) + "");
                }
            }
        });

        logOnTaskFinish(taskName, posts);
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
            logger.warning("Unable to send a request and/or receive a response for request \"" + requestUrl + "\"");

        } catch (NullPointerException | JsonParseException | ClassCastException | IllegalStateException  e) {
            logger.warning("Retrieved malformed JSON format for request \"" + requestUrl + "\"");
        
        } catch (URISyntaxException e) {
            // Do nothing
        }

        return List.of();
    }

}