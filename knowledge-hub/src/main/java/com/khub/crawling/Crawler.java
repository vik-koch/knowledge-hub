package com.khub.crawling;

import java.net.http.HttpClient;
import java.util.logging.Logger;

/**
 * Abstract API Crawler
 */
public abstract class Crawler {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    protected final HttpClient client = HttpClient.newHttpClient();

    // Base URL and bearer token
    protected final String url;
    protected final String[] header;

    // Constructor
    public Crawler(String url, String[] header) {
        this.url = url;
        this.header = header;
    }

    /**
     * Checks if the given string is null or empty 
     * @param str - the input string
     * @return true if the string is null or empty
     */
    protected static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
