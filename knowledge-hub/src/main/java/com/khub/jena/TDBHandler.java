package com.khub.jena;

import java.util.Properties;

import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;

import com.khub.exceptions.InvalidConfigurationException;

// Wrapper class for TDBFactory
public class TDBHandler {

    private static final String PATH_KEY = "tdb.path";

    // Prevents instantiation
    private TDBHandler() {
    }

    /**
     * 
     * @param configuration
     * @return
     * @throws InvalidConfigurationException
     */
    public static Dataset getDataset(Properties configuration) throws InvalidConfigurationException {
        String path = configuration.getProperty(PATH_KEY);
        if (path == null || path.isEmpty()) {
            throw new InvalidConfigurationException("The given URL is null or empty");
        }
        return TDB2Factory.connectDataset(path);
    }
}
