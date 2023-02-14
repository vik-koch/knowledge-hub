package com.khub.crawling;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestBuilder {

    private static final Logger logger = Logger.getLogger(HttpRequestBuilder.class.getName());

    // Prevents instantiation
    private HttpRequestBuilder() {
    }

    /**
     * Builds and returns an {@link HttpRequest}
     * @param uri - the request {@code URI} as {@link String}
     * @param headers - the request headers as name value pairs 
     * @return the built {@link HttpRequest}
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
            logger.log(Level.SEVERE, "Invalid request URI format for " + uri);
        }

        return null;
    }

}