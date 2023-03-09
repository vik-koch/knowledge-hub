package com.khub.enriching;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.tdb2.TDB2Factory;
import org.jsoup.Jsoup;

public class KnowledgeEnricher {

    protected static final Logger logger = Logger.getLogger(KnowledgeEnricher.class.getName());

    private final Dataset tdb;

    private KnowledgeEnricher(Dataset tdb) {
        this.tdb = tdb;
    }

    /**
     * Returns an instance of {@link KnowledgeEnricher} if the given {@tdbPath}
     * is valid and the TDB store directory can be created
     * @param tdbPath - the {@link Path} to the TDB store
     * @return the {@link KnowledgeEnricher}
     */
    public static KnowledgeEnricher of(Path tdbPath) {
        try {
            Files.createDirectories(tdbPath);
            return new KnowledgeEnricher(TDB2Factory.connectDataset(tdbPath.toString()));
        } catch (Exception e) {
            logger.severe("Unable to create the TDB store directory at \"" + tdbPath + "\"");
            return null;
        }
    }

    /**
     * Starts {@link KnowledgeEnricher} for creating new triples by linking knowledge artifacts
     * to the content resources found in their text content. The process iterates over {@code n}
     * knowledge artifacts and {@code m} content resources and has time complexity of {@code n*m}
     * @param contentModelName - the name of the {@link Model} with data from knowledge artifacts
     * @param knowledgeModelName - the name of the {@link Model} with knowledge artifacts
     * @param keywordsModelName - the name of the {@link Model} for keywords enriching
     * @param ontologyIri - the ontology IRI for knowledge artifacts
     * @param contentPredicate - the predicate name of knowledge artifacts' content
     * @param keywordPredicate - the predicate name for new keywords triples
     * @return true if the step runned successfully, false otherwise
     */
    public boolean run(String contentModelName, String knowledgeModelName, String keywordsModelName, 
                    URL ontologyIri, String contentPredicate, String keywordPredicate) {

        Map<RDFNode, String> contentResources = new HashMap<RDFNode, String>();
        Map<RDFNode, String> knowledgeArtifacts = new HashMap<RDFNode, String>();

        try {
            // Get content resources (subject nodes)
            Model contentModel = tdb.getNamedModel(contentModelName);
            tdb.executeRead(() -> contentResources.putAll(retrieveContentResources(contentModel)));

            // Get knowledge artifacts with content as plain text
            Model knowledgeModel = tdb.getNamedModel(knowledgeModelName);
            tdb.executeRead(() -> knowledgeArtifacts.putAll(retrieveKnowledgeArtifacts(knowledgeModel, ontologyIri, contentPredicate)));

            // Iterator for progress logging
            Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            int step = 10; // Step in percent
            for (int i = step; i <= 100; i += step) {
                map.put(knowledgeArtifacts.size() * i / 100, i);
            }

            Model keywordsModel = tdb.getNamedModel(keywordsModelName);
            Property keywordProperty = keywordsModel.createProperty(ontologyIri + "#" + keywordPredicate);

            int index = 0;
            for (RDFNode artifact : knowledgeArtifacts.keySet()) {
                for (RDFNode resource : contentResources.keySet()) {
                    if (knowledgeArtifacts.get(artifact).contains(contentResources.get(resource))) {
                        // Add a new triple if content resource is found in the content of a knowledge artifact
                        tdb.executeWrite(() -> keywordsModel.add(artifact.asResource(), keywordProperty, resource));
                    }
                }

                index++;
                if (map.keySet().contains(index)) {
                    logger.info("Processed " + map.get(index) + "% of all knowledge artifacts");
                }
            }
            return true;

        } catch (Exception e) {
            logger.severe("Unable to enrich the knowledge graph");
            return false;
        }
    }

    /**
     * Retrieves all subject node names (String content after {@code #} in subject namespace IRI) mapped
     * to the corresponding subject nodes as {@link RDFnode} from the given {@code contentModel}
     * @param contentModel - the {@link Model} with data from knowledge artifacts
     * @return the {@link Map} with subject nodes and their names
     */
    private Map<RDFNode, String> retrieveContentResources(Model contentModel) {

        Map<RDFNode, String> contentResources = new HashMap<RDFNode, String>();
        String query = "SELECT DISTINCT ?subject WHERE {?subject ?predicate ?object}";

        try (QueryExecution qExec = QueryExecution.model(contentModel).query(query).build()) {
            ResultSet result = qExec.execSelect();
            while (result.hasNext()) {
                QuerySolution solution = result.nextSolution();
                RDFNode subjectNode = solution.get("subject");

                String subjectString = subjectNode.toString();
                String subjectLiteral = subjectString.substring(subjectString.lastIndexOf('#') + 1);
                contentResources.put(subjectNode, subjectLiteral);
            }
        }

        logger.info("Retrieved " + contentResources.size() + " content resources");
        return contentResources;
    }

    /**
     * Retrieves all subject nodes from {@code knowledgeModel} that have a predicate 
     * {@code ontologyIri#contentPredicate} mapped to the corresponding object parsed as visible text
     * @param knowledgeModel - the {@link Model} with knowledge artifacts
     * @param ontologyIri - the ontology IRI for knowledge artifacts
     * @param contentPredicate - the predicate name of knowledge artifacts' content
     * @return the {@link Map} with subject nodes and their content as plain text
     */
    private Map<RDFNode, String> retrieveKnowledgeArtifacts(Model knowledgeModel, URL ontologyIri, String contentPredicate) {

        Map<RDFNode, String> knowledgeArtifacts = new HashMap<RDFNode, String>();
        String query = "PREFIX predicate: <" + ontologyIri + "#> "
            + "SELECT ?subject ?object "
            + "WHERE {?subject predicate:" + contentPredicate + " ?object}";

        try (QueryExecution qExec = QueryExecution.model(knowledgeModel).query(query).build()) {
            ResultSet result = qExec.execSelect();
            while (result.hasNext()) {
                QuerySolution solution = result.nextSolution();
                RDFNode subjectNode = solution.get("subject");

                String objectString = solution.get("object").toString();
                String objectContentText = Jsoup.parse(objectString).text();
                if (!objectContentText.isEmpty()) knowledgeArtifacts.put(subjectNode, objectContentText);
            }
        }

        logger.info("Retrieved " + knowledgeArtifacts.size() + " knowledge artifacts");
        return knowledgeArtifacts;
    }

}