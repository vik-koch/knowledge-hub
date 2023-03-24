package com.khub.misc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.logging.Level;
import java.util.logging.Logger;

// Wrapper class for HttpRequest Builder
public class HttpRequestBuilder {

    private static final Logger logger = Logger.getLogger(Configuration.class.getName());

    // Prevents instantiation
    private HttpRequestBuilder() {
    }

    /**
     * Builds and returns an {@code HttpRequest}
     * @param uri - request {@code URI}
     * @param headers - request headers as name value pairs 
     * @return a new {@code HttpRequest}
     */
    public static HttpRequest build(String uri, String... headers) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(new URI(uri))
                .headers(headers)
                .build();
            return request;

        } catch (URISyntaxException e) {
            logger.log(Level.SEVERE, "Invalid request URI format", e);
        }

        return null;
    }
}
