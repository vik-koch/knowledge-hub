package com.khub.extracting;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.jena.dboe.DBOpEnvException;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.tdb2.TDB2Factory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.khub.common.FilesHelper;

public class ContentExtractor {

    protected final static Logger logger = Logger.getLogger(ContentExtractor.class.getName());

    private final Dataset tdb;

    private ContentExtractor(Dataset tdb) {
        this.tdb = tdb;
    }

    /**
     * Returns an instance of {@link ContentExtractor} if the given {@tdbPath}
     * is valid and the TDB store directory can be created
     * @param tdbPath - the {@link Path} to the TDB store
     * @return the {@link ContentExtractor}
     */
    public static ContentExtractor of(Path tdbPath) {
        try {
            FilesHelper.createDirectories(tdbPath);
            return new ContentExtractor(TDB2Factory.connectDataset(tdbPath.toString()));
        } catch (DBOpEnvException e) {
            logger.severe("The TDB store at \"" + tdbPath + "\" is locked");
        }
        return null;
    }

    /**
     * Starts {@link ContentExtractor} for extracting document content as {@link JSON}
     * to the folder with {@code outputDirectoryName} under the given {@code contentPath}
     * @param contentPath - the {@link Path} for {@code content} data
     * @param queriesPath - the {@link Path} for queries to retrieve {@code content} data
     * @param outputDirectoryName - the directory name to save extracted files to
     * @param contentPredicate - the predicate name of knowledge artifacts' content
     */
    public boolean run(Path contentPath, Path queriesPath, String outputDirectoryName, String contentPredicate) {

        Map<String, Object> resultMap = new HashMap<String, Object>();

        // Prepare for iteration
        Model model = tdb.getUnionModel();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<String> filenames = FilesHelper.getFilenamesForPath(queriesPath);

        if (filenames.size() == 0) {
            logger.severe("No queries for content extraction were found at \"" + contentPath + "\"");
            return false;
        }

        Path outputPath = FilesHelper.createDirectories(contentPath.resolve(outputDirectoryName));
        if (outputPath == null) {
            return false;
        }

        for (String filename : filenames) {
            try {
                Path queryPath = queriesPath.resolve(filename);
                if (!Files.exists(queryPath)) continue;

                resultMap.clear();
                String queryString = Files.readString(queryPath, StandardCharsets.UTF_8);

                try (QueryExecution qExec = QueryExecution.model(model).query(queryString).build()) {
                    tdb.executeRead(() -> {
                        ResultSet result = qExec.execSelect();
                        while (result.hasNext()) {
    
                            // Map for documents that can contain Strings or Lists as values
                            Map<String, Object> documentMap = new HashMap<String, Object>();
    
                            // Get the query result associated with the query
                            Map<String, String> queryResult = parseQueryResult(result.nextSolution());
    
                            // Check if the query result contains content property
                            if (queryResult.containsKey(contentPredicate)) {
                                String content = queryResult.remove(contentPredicate);
    
                                // Parse all tables in HTML content body
                                Elements tables = Jsoup.parse(content).getElementsByTag("table");
                                for (Element table : tables) {
                                    String tableKey = "Table_" + tables.indexOf(table);
                                    documentMap.put(tableKey, parseTableContent(table));
                                }
                            }
    
                            documentMap.putAll(queryResult);
                            resultMap.put("Result_" + result.getRowNumber(), documentMap);
                        }
                    });
                }
            } catch (Exception e) {
                logger.severe("Unable to get content for the \"" + filename + "\" file");
                break;
            }

            try {
                // Convert maps to JSON and write to file
                filename = filename.substring(0, filename.lastIndexOf('.')) + ".json";
                Path outputFilePath = contentPath.resolve(outputDirectoryName).resolve(filename);
                try (BufferedWriter writer = Files.newBufferedWriter(outputFilePath, StandardCharsets.UTF_8)) {
                    gson.toJson(resultMap, writer);
                    logger.info("Extracted and saved content for the \"" + filename + "\" file");
                }
            } catch (Exception e) {
                logger.severe("Unable to extract content for the \"" + filename + "\" file");
            }
        }

        return true;
    }

    /**
     * Returns a {@link Map} of query variable names mapped to
     * their values retrieved from the given {@link QuerySolution}
     * @param solution - the result of a {@code SELECT} query
     * @return the mapped result of the query
     */
    private Map<String, String> parseQueryResult(QuerySolution solution) {
        Map<String, String> queryResult = new HashMap<String, String>();
        Iterator<String> varNames = solution.varNames();

        while(varNames.hasNext()) {
            String name = varNames.next();
            RDFNode node = solution.get(name);

            if (node != null) {
                queryResult.put(name, node.toString());
            }
        }

        return queryResult;
    }

    /**
     * Parses the given {@code HTML} table and converts it to a {@link List}
     * of key-value pairs mapped from <th> headers to <td> values. Produces
     * artificial header keys if the table does not contain a header row.
     * @param table - the {@code Table} as {@link Element} to be parsed
     * @return the table content in {@code JSON}-like format
     */
    public List<Map<String, String>> parseTableContent(Element table) {
        List<Map<String, String>> output = new ArrayList<Map<String, String>>();
        
        List<Element> rows = table.getElementsByTag("tr");
        if (rows.size() < 1) return output;

        List<String> headers = new ArrayList<String>();
        List<Element> headerElements = rows.get(0).children();

        // Check if there are table headers <th> in the first row
        boolean thHeaders = rows.get(0).getElementsByTag("th").size() != 0;

        // Populate headers from <th> or artificial
        for (Element element : headerElements) {
            if (thHeaders && element.hasText()) {
                headers.add(element.text());
            }
            else {
                headers.add("Row_" + headerElements.indexOf(element));
            }
        }

        // Populate content with "" for empty <td>
        rows.stream().skip(1).forEach(row -> {
            List<Element> bodyElements = row.children();
            Map<String, String> documentMap = new HashMap<String, String>();
            for (String header : headers) {
                Element element = bodyElements.get(headers.indexOf(header));
                String content = "";
                if (element.hasText()) {
                    content = element.text();
                }
                documentMap.put(header, content);
            }
            output.add(documentMap);
        });
        return output;
    }

}