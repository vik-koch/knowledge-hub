package com.khub.crawling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.khub.exceptions.InvalidConfigurationException;
import com.khub.misc.HttpRequestBuilder;

/**
 * MS Teams API Crawler
 */
public class TeamsCrawler extends Crawler {

    private static final String URL_KEY = "teams.url";
    private static final String TOKEN_KEY = "teams.token";
    private static final String HEADER_KEY = "Authorization";
    private static final String PAYLOAD_KEY = "Bearer";

    private List<JsonElement> teams = new ArrayList<JsonElement>();
    private List<JsonElement> channels = new ArrayList<JsonElement>();
    private List<JsonElement> messages = new ArrayList<JsonElement>();
    private List<JsonElement> replies = new ArrayList<JsonElement>();

    // Constructor
    private TeamsCrawler(String url, String[] header) {
        super(url, header);
    }

    /**
     * Instantiates a {@code TeamsCrawler} if provided
     * configuration is plausible, throws otherwise
     * @param configuration - {@code Properties} object
     * @return a {@code TeamsCrawler} instance
     * @throws InvalidConfigurationException - if configuration is false
     */
    public static TeamsCrawler of(Properties configuration) throws InvalidConfigurationException {
        String url = configuration.getProperty(URL_KEY);
        String token = configuration.getProperty(TOKEN_KEY);
        String[] header = new String[] {HEADER_KEY, PAYLOAD_KEY + ' ' + token};

        // TODO: Check URL, alternatively also:
        // 1. Ping the URL to check the connection
        // 2. Send simple GET request to check the token correctness
        if (isNullOrEmpty(url) || isNullOrEmpty(token)) {
            throw new InvalidConfigurationException("The given URL or token is null or empty");
        }

        return new TeamsCrawler(url, header);
    }

    /**
     * Runs the retrieval process from MS Teams that cascades over teams, channels and messages from MS Teams.
     * @return all retrieved data as {@code HashMap} of {@code Lists} with {@code JsonElements}
     */

    // TODO: Retrieve more SharePoint data like Exchange Calendar events
    public HashMap<String, List<JsonElement>> run() {
        logger.log(Level.INFO, "Retrieval of MS Teams content started");

        teams.clear();
        channels.clear();
        messages.clear();
        replies.clear();

        teams.addAll(retrieveTeams());

        teams.parallelStream().forEach(team -> {
            String teamId = team.getAsJsonObject().get("id").getAsString();
            List<JsonElement> teamChannels = retrieveChannels(teamId);
            channels.addAll(teamChannels);

            teamChannels.parallelStream().forEach(channel -> {
                String channelId = channel.getAsJsonObject().get("id").getAsString();
                List<List<JsonElement>> messagesWithReplies = retrieveMessagesWithReplies(teamId, channelId);
                messages.addAll(messagesWithReplies.get(0));
                replies.addAll(messagesWithReplies.get(1));
            });
        });

        logger.log(Level.INFO, "Retrieval of MS Teams content finished");

        HashMap<String, List<JsonElement>> jsonElements = new HashMap<String, List<JsonElement>>();
        jsonElements.put("teams", teams);
        jsonElements.put("channels", channels);
        jsonElements.put("messages", messages);
        jsonElements.put("replies", replies);
        return jsonElements;
    }

    /**
     * Retrieves all teams from MS Teams, of which the request sender is member as {@code JSON}
     * @return a {@code List} with teams as {@code JSON} objects
     */
    private List<JsonElement> retrieveTeams() {
        List<JsonElement> teams = new ArrayList<JsonElement>();

        String uri = this.url + "v1.0/me/joinedTeams";
        teams.addAll(retrieve(uri));

        if (!teams.isEmpty()) {
            logger.log(Level.INFO, teams.size() + " teams were retrieved");
        } else {
            logger.log(Level.WARNING, "No Teams were retrieved");
        }

        return teams;
    }

    /**
     * Retrieves all channels for the given team from MS Teams as {@code JSON}
     * @param teamId - the ID of the team
     * @return a {@code List} with channels as {@code JSON} objects
     */
    private List<JsonElement> retrieveChannels(String teamId) {
        List<JsonElement> channels = new ArrayList<JsonElement>();

        String uri = this.url + "v1.0/teams/" + teamId + "/channels";
        List<JsonElement> jsonElements = retrieve(uri);
        for (JsonElement jsonElement : jsonElements) {
            jsonElement.getAsJsonObject().addProperty("teamId", teamId);
            channels.add(jsonElement);
        }

        if (!channels.isEmpty()) {
            logger.log(Level.INFO, channels.size() + " team channels for team with ID " + teamId + " were retrieved");
        } else {
            logger.log(Level.WARNING, "No team channels for team with ID " + teamId + " were retrieved");
        }

        return channels;
    }

    /**
     * Retrieves all messages with replies for the given channel from MS Teams as {@code JSON}
     * @param teamId - the ID of the team
     * @param channelId - the ID of the channel
     * @return a {@code List} with messages and replies as {@code JSON} objects
     */
    private List<List<JsonElement>> retrieveMessagesWithReplies(String teamId, String channelId) {
        List<JsonElement> messages = new ArrayList<JsonElement>();
        List<JsonElement> replies = new ArrayList<JsonElement>();

        String uri = this.url + "v1.0/teams/" + teamId + "/channels/" + channelId + "/messages?$expand=replies&top=100";
        List<JsonElement> jsonElements = retrieve(uri);

        for (JsonElement jsonElement : jsonElements) {
            JsonObject message = jsonElement.getAsJsonObject();
            if (!message.get("messageType").getAsString().equals("message")) {
                continue;
            }

            JsonArray replyArray = message.getAsJsonArray("replies");
            if (replyArray.size() != 0) {
                replyArray.forEach(reply -> {
                    if (reply.getAsJsonObject().get("messageType").getAsString().equals("message")) {
                        replies.add(reply);
                    }
                });
                message.remove("replies@odata.count");
            }

            message.remove("replies");
            message.remove("replies@odata.context");
            messages.add(jsonElement);
        }

        if (!messages.isEmpty()) {
            logger.log(Level.INFO, messages.size() + " messages and " + replies.size() +
                       " replies for channel with ID " + channelId + " were retrieved");
        } else {
            logger.log(Level.WARNING, "No messages for channel with ID " + channelId + " were retrieved");
        }

        return Arrays.asList(messages, replies);
    }

    /**
     * Common method for retrieving content from MS Teams using the given API request.
     * The number of responses is limited to 20 with @odata.nextLink provided for the
     * follow-up request containing the other responses.
     * @param uri - specific request {@code URI}
     * @return response as {@code JsonArray} or null if failed
     */
    private List<JsonElement> retrieve(String uri) {
        JsonArray jsonArray = new JsonArray();

        try {
            do {
                HttpRequest request = HttpRequestBuilder.build(uri, header);
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

                JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                jsonArray.addAll(jsonObject.getAsJsonArray("value"));

                JsonElement nextLink = jsonObject.get("@odata.nextLink");
                uri = nextLink != null ? nextLink.getAsString() : null;
            } while (uri != null);

            return jsonArray.asList();

        } catch (InterruptedException | IOException e) {
            logger.log(Level.SEVERE, "Unable to send a request and/or receive a response", e);

        } catch (JsonSyntaxException e) {
            logger.log(Level.SEVERE, "Retrieved malformed JSON format", e);
        }

        return null;
    }
}
