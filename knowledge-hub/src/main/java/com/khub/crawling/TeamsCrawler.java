package com.khub.crawling;

import java.util.HashMap;
import java.util.HashSet;
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

    private HashSet<JsonElement> teams = new HashSet<JsonElement>();
    private HashSet<JsonElement> channels = new HashSet<JsonElement>();
    private HashSet<JsonElement> messagesWithReplies = new HashSet<JsonElement>();

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
     * @return all retrieved data as {@code HashMap} of {@code HashSets} with {@code JsonElements}
     */

    // TODO: Replace for-each loops with concurrent threads
    // TODO: Retrieve more SharePoint data Exchange Calendar events
    public HashMap<String, HashSet<JsonElement>> run() {
        logger.log(Level.INFO, "Retrieval of MS Teams content started");

        teams.clear();
        channels.clear();
        messagesWithReplies.clear();

        teams.addAll(retrieveTeams());

        for (JsonElement team : teams) {
            String teamId = team.getAsJsonObject().get("id").getAsString();
            HashSet<JsonElement> teamChannels = retrieveChannels(teamId);
            channels.addAll(teamChannels);

            for (JsonElement channel : teamChannels) {
                String channelId = channel.getAsJsonObject().get("id").getAsString();
                messagesWithReplies.addAll(retrieveMessagesWithReplies(teamId, channelId));
            }
        }

        logger.log(Level.INFO, "Retrieval of MS Teams content finished");
        logger.log(Level.INFO, "Retrieved " + teams.size() + " teams, "+ channels.size() 
            + " channels and " + messagesWithReplies.size() + " messages");

        return new HashMap<String, HashSet<JsonElement>>() {{
                put("teams", teams);
                put("channels", channels);
                put("messagesWithReplies", messagesWithReplies);
        }};
    }

    /**
     * Retrieves all teams from MS Teams, of which the request sender is member as {@code JSON}
     * @return a {@code HashSet} with teams as {@code JSON} objects
     */
    private HashSet<JsonElement> retrieveTeams() {
        HashSet<JsonElement> teams = new HashSet<JsonElement>();

        String uri = this.url + "v1.0/me/joinedTeams";
        teams.addAll(retrieve(uri).asList());

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
     * @return a {@code HashSet} with channels as {@code JSON} objects
     */
    private HashSet<JsonElement> retrieveChannels(String teamId) {
        HashSet<JsonElement> channels = new HashSet<JsonElement>();

        String uri = this.url + "v1.0/teams/" + teamId + "/channels";
        channels.addAll(retrieve(uri).asList());

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
     * @return a {@code HashSet} with messages and replies as {@code JSON} objects
     */
    private HashSet<JsonElement> retrieveMessagesWithReplies(String teamId, String channelId) {
        HashSet<JsonElement> messages = new HashSet<JsonElement>();

        String uri = this.url + "v1.0/teams/" + teamId + "/channels/" + channelId + "/messages?$expand=replies";
        List<JsonElement> list = retrieve(uri).asList();
        list.removeIf(message -> (!message.getAsJsonObject().get("messageType").getAsString().equals("message")));
        messages.addAll(list);

        if (!messages.isEmpty()) {
            logger.log(Level.INFO, messages.size() + " messages for channel with ID " + channelId + " were retrieved");
        } else {
            logger.log(Level.WARNING, "No messages for channel with ID " + channelId + " were retrieved");
        }

        return messages;
    }

    /**
     * Common method for retrieving content from MS Teams using the given API request.
     * The number of responses is limited to 20 with @odata.nextLink provided for the
     * follow-up request containing the other responses.
     * @param uri - specific request {@code URI}
     * @return response as {@code JsonArray} or null if failed
     */
    private JsonArray retrieve(String uri) {
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

            return jsonArray;

        } catch (InterruptedException | IOException e) {
            logger.log(Level.SEVERE, "Unable to send a request and/or receive a response", e);

        } catch (JsonSyntaxException e) {
            logger.log(Level.SEVERE, "Retrieved malformed JSON format", e);
        }

        return null;
    }
}
