package com.khub.common;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

import com.mongodb.ConnectionString;

public class Configuration {

    private static final Logger logger = Logger.getLogger(Configuration.class.getName());

    private final Properties properties;

    public final URL confluenceEndpoint;
    public final AuthenticationHeader confluenceHeader;

    public final URL teamsEndpoint;
    public final AuthenticationHeader teamsHeader;

    public final ConnectionString mongoConnectionString;

    public final Path dockerPath;

    public final Path processingPath;

    public final Path knowledgePath;
    public final Path contentPath;

    public final Path ontologyPath;

    public final Path queriesPath;

    public final Path tdbPath;

    public final String knowledgeModelName;
    public final String ontologyModelName;
    public final String contentModelName;
    public final String referenceModelName;

    public final URL ontologyIri;
    public final String contentPredicate;
    public final String titlePredicate;
    public final String referencePredicate;

    public Configuration(Properties properties) {
        this.properties = properties;

        confluenceEndpoint = parseUrl("confluence.endpoint");
        String confluenceHeaderKey = parseString("confluence.header.key");
        String confluenceHeaderValue = parseString("confluence.header.value");
        String confluenceToken = parseString("confluence.token");
        confluenceHeader = new AuthenticationHeader(confluenceHeaderKey, confluenceHeaderValue, confluenceToken);

        teamsEndpoint = parseUrl("teams.endpoint");
        String teamsHeaderKey = parseString("teams.header.key");
        String teamsHeaderValue = parseString("teams.header.value");
        String teamsToken = parseString("teams.token");
        teamsHeader = new AuthenticationHeader(teamsHeaderKey, teamsHeaderValue, teamsToken);

        mongoConnectionString = parseConnectionString("mongo.connection.string");

        dockerPath = parsePath("docker.path");

        processingPath = parsePath("processing.path");

        knowledgePath = parsePath("knowledge.path");
        contentPath = parsePath("content.path");

        ontologyPath = parsePath("ontology.path");

        queriesPath = parsePath("queries.path");

        tdbPath = parsePath("tdb.path");

        knowledgeModelName = parseString("knowledge.model.name");
        ontologyModelName = parseString("ontology.model.name");
        contentModelName = parseString("content.model.name");
        referenceModelName = parseString("reference.model.name");

        ontologyIri = parseUrl("ontology.iri");
        contentPredicate = parseString("content.predicate");
        titlePredicate = parseString("title.predicate");
        referencePredicate = parseString("reference.predicate");
    }

    /**
     * Parses and validates property as {@link String}
     * @param key - the property key 
     * @return the {@link String} value in {@link Properties} with the specified key
     */
    private String parseString(String key) {
        String property = properties.getProperty(key);
        try {
            validateNotNullOrEmpty(property);
            return property;
        } catch (IllegalArgumentException e) {
            logInvalidPropertyValue(key);
            return null;
        }
    }

    /**
     * Parses and validates property as {@link ConnectionString}
     * @param key - the property key 
     * @return the {@link ConnectionString} value in {@link Properties} with the specified key
     */
    private ConnectionString parseConnectionString(String key) {
        String property = properties.getProperty(key);
        try {
            validateNotNullOrEmpty(property);
            return new ConnectionString(property);
        } catch (IllegalArgumentException e) {
            logInvalidPropertyValue(key);
            return null;
        }
    }

    /**
     * Parses and validates property as {@link Path}
     * @param key - the property key 
     * @return the {@link Path} value in {@link Properties} with the specified key
     */
    private Path parsePath(String key) {
        String property = properties.getProperty(key);
        try {
            validateNotNullOrEmpty(property);
            return Paths.get(property);
        } catch (IllegalArgumentException e) {
            logInvalidPropertyValue(key);
            return null;
        }
    }

    /**
     * Parses and validates property as {@link URL}
     * @param key - the property key 
     * @return the {@link URL} value in {@link Properties} with the specified key
     */
    private URL parseUrl(String key) {
        String property = properties.getProperty(key);
        try {
            validateNotNullOrEmpty(property);
            return new URL(property);
        } catch (MalformedURLException | IllegalArgumentException e) {
            logInvalidPropertyValue(key);
            return null;
        }
    }

    /**
     * Validates if the given string is null or empty
     * @param property - the string to validate
     * @throws IllegalArgumentException - if the string is null or empty
     */
    private void validateNotNullOrEmpty(String property) throws IllegalArgumentException {
        if (property == null || property.isEmpty()) {
            throw new IllegalArgumentException(property);
        }
    }

    private void logInvalidPropertyValue(String key) {
        logger.info("Invalid or null value for \"" + key + "\" is provided");
    }

}