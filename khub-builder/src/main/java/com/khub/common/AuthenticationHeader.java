package com.khub.common;

import java.util.Base64;
import java.util.Objects;

public class AuthenticationHeader {
    
    private final String headerKey;
    private final String headerValue;
    private final String token;

    private final boolean noHeader;

    public AuthenticationHeader(String headerKey, String headerValue, String token) {
        this.headerKey = headerKey;
        this.headerValue = headerValue;
        this.token = Objects.equals(headerValue, "Basic") && token != null
            ? Base64.getEncoder().encodeToString(token.getBytes())
            : token;

        this.noHeader = token != null && Objects.equals(token, "null");
    }

    /**
     * Converts authentication header to a name-value pair
     * @return the name-value pair
     */
    public String[] toNameValuePair() {
        if (noHeader) return null;
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