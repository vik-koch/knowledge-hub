package com.khub.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
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
     * @throws URISyntaxException - if the given uri is falformed
     */
    public static HttpRequest build(String uri, String... headers) throws URISyntaxException {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().GET().uri(new URI(uri));
            return headers == null ? requestBuilder.build() : requestBuilder.headers(headers).build();

        } catch (URISyntaxException | NullPointerException | IllegalArgumentException e) {
            logger.warning("Invalid request URI format for \"" + uri + "\"");
            throw new URISyntaxException(uri, e.getMessage());
        }
    }

}