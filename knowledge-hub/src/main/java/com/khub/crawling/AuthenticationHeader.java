package com.khub.crawling;

import java.util.Base64;
import java.util.Objects;

public class AuthenticationHeader {
    
    private final String headerKey;
    private final String headerValue;
    private final String token;

    public AuthenticationHeader(String headerKey, String headerValue, String token) {
        this.headerKey = headerKey;
        this.headerValue = headerValue;
        this.token = Objects.equals(headerValue, "Basic") && token != null
            ? Base64.getEncoder().encodeToString(token.getBytes())
            : token;
    }

    /**
     * Converts authentication header to a name-value pair
     * @return the name-value pair
     */
    public String[] toNameValuePair() {
        return new String[] {headerKey, headerValue + " " + token};
    }

    /**
     * Check if all header values are not null
     * @return True if all header values are not null
     */
    public boolean isValid() {
        return headerKey != null && headerValue != null && token != null;
    }

}