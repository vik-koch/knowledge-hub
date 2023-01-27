package com.khub.mapping;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.khub.exceptions.InvalidConfigurationException;
import com.khub.jena.TDBHandler;
import com.khub.misc.Configuration;

public class ContentMappingRunner {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());
    protected final List<String> attributes = Arrays.asList("ri:space-key", "ri:userkey", null);

    /**
     * Runs content mapping process for files in {@code content-mappings} folder
     * and writes mapped files to the path under {@code content-mappings/output} 
     * given in {@code configuration}
     * @param configuration - {@code Properties} object
     */
    public void run(Properties configuration) {
        // Prepares dataset
        try {
            Dataset tdb = TDBHandler.getDataset(configuration);

            String path = configuration.getProperty("content.mappings.path");

            Set<String> fileNames = Files.list(Paths.get(path))
                .filter(file -> !Files.isDirectory(file))
                .map(Path::getFileName)
                .map(Path::toString)
                .map(str -> str.substring(0, str.lastIndexOf('.')))
                .collect(Collectors.toSet());

            String absolutePath = new File(path).getAbsolutePath();
            RMLMapper mapper = new RMLMapper(path);

            try {
                Files.createDirectories(Paths.get(path + File.separator + "csvs"));
                Files.createDirectories(Paths.get(path + File.separator + "output"));
            } catch (IOException e) {
                throw new IOException("Unable to create output folders", e);
            }

            for (String fileName : fileNames) {
                File mapping = new File(absolutePath + File.separator + fileName + ".ttl");
                File query = new File(absolutePath + File.separator + fileName + ".rq");

                if (!mapping.isFile() || !query.isFile()) {
                    continue;
                }

                String queryString = Files.readString(query.toPath(), StandardCharsets.UTF_8);
                String outputFile = absolutePath + File.separator + "csvs" + File.separator + fileName + ".csv";

                tdb.begin(ReadWrite.READ);
                try (QueryExecution qExec = QueryExecution.dataset(tdb).query(queryString).build()) {
                    ResultSet result = qExec.execSelect();
                    while (result.hasNext()) {
                        QuerySolution solution = result.nextSolution();
                        String content = solution.get("content").toString();
                        Document document = Jsoup.parse(content);
        
                        for (Element table : document.getElementsByTag("table")) {
                            List<String> output = parseTable(table);
                            Files.write(Paths.get(outputFile), output, StandardCharsets.UTF_8);
                            mapper.executeMapping(fileName);
                        }
                    }
                }
                tdb.end();
            }

        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Invalid configuration provided", e);
            return;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "No files for import were found", e);
            return;
        }
    }

    /**
     * Parses a table as HTML {@code Element} and returns the table
     * content in CSV format as List of Strings
     * @param table - HTML {@code Element}
     * @return table content in CSV format
     */
    public List<String> parseTable(Element table) {
        List<String> output = new ArrayList<String>();
        int columnNumber = 0;

        for (Element row : table.getElementsByTag("tr")) {
            List<String> line = new ArrayList<>();

            Elements cells = row.getElementsByTag("th");
            cells.addAll(row.getElementsByTag("td"));

            for (Element cell : cells) {
                for (String attribute : attributes) {
                    String value = getValue(cell, attribute);
                    if (value != null) {
                        line.add(value);
                        break;
                    }
                }
            }

            line.removeIf(String::isEmpty);
            line.removeIf(str -> str.chars().allMatch(Character::isDigit));
            if (output.size() == 0) columnNumber = line.size();
            if (line.size() < columnNumber) continue;
            output.add(String.join(",", line));
        }

        return output;
    }

    /**
     * Returns value of a HTML {@code Element} as String for the given attribute.
     * Returns normalized text of the element if attribute is not provided
     * @param element - HTML element
     * @param attribute - attribute name
     * @return value of a HTML element
     */
    public String getValue(Element element, String attribute) {
        if (attribute == null) return element.text();
        Element attrElement = element.getElementsByAttributeStarting(attribute).first();
        if (attrElement != null) {
            String attrString = attrElement.attr(attribute);
            if (attrString.length() > 3) {
                return attrString.substring(2, attrString.length() - 2);
            }
        }
        return attribute == null ? element.text() : null;
    }

    // Temporary method for testing
    public static void main(String[] args) throws InvalidConfigurationException, IOException {
        String configPath = String.join(File.separator, "knowledge-hub", "src", "main", "resources", "config.properties");
        Properties configuration = Configuration.initialize(configPath);
        ContentMappingRunner cmr = new ContentMappingRunner();
        cmr.run(configuration);
    }
}
