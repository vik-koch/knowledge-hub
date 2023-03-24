package com.khub.enriching;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.jena.dboe.DBOpEnvException;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.tdb2.TDB2Factory;
import org.jsoup.Jsoup;

import com.khub.common.FilesHelper;

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
            FilesHelper.createDirectories(tdbPath);
            return new KnowledgeEnricher(TDB2Factory.connectDataset(tdbPath.toString()));
        } catch (DBOpEnvException e) {
            logger.severe("The TDB store at \"" + tdbPath + "\" is locked");
        }
        return null;
    }

    /**
     * Starts {@link KnowledgeEnricher} for creating new triples by linking knowledge artifacts
     * to the content resources found in their text content. The process iterates over {@code n}
     * knowledge artifacts and {@code m} content resources and has time complexity of {@code n*m}
     * @param contentModelName - the name of the {@link Model} with data from knowledge artifacts
     * @param knowledgeModelName - the name of the {@link Model} with knowledge artifacts
     * @param referenceModelName - the name of the {@link Model} for reference enriching
     * @param ontologyIri - the ontology IRI for knowledge artifacts
     * @param contentPredicate - the predicate name of knowledge artifacts' content
     * @param titlePredicate - the predicate name for the title of the content nodes
     * @param referencePredicate - the predicate name for new reference triples
     * @return true, if the step runned successfully, false otherwise
     */
    public boolean run(String contentModelName, String knowledgeModelName, String referenceModelName, 
                    URL ontologyIri, String contentPredicate, String titlePredicate, String referencePredicate) {

        Map<RDFNode, String> contentResources = new HashMap<RDFNode, String>();
        Map<RDFNode, String> knowledgeArtifacts = new HashMap<RDFNode, String>();

        try {
            // Get content resources (subject nodes)
            Model contentModel = tdb.getNamedModel(contentModelName);
            tdb.executeRead(() -> contentResources.putAll(retrieveContentResources(contentModel, ontologyIri, titlePredicate)));

            if (contentResources.size() == 0) {
                logger.severe("No content resources were found");
                return false;
            }

            // Get knowledge artifacts with content as plain text
            Model knowledgeModel = tdb.getNamedModel(knowledgeModelName);
            tdb.executeRead(() -> knowledgeArtifacts.putAll(retrieveKnowledgeArtifacts(knowledgeModel, ontologyIri, contentPredicate, titlePredicate)));

            if (knowledgeArtifacts.size() == 0) {
                logger.severe("No knowledge artifacts were found");
                return false;
            }

            // Iterator for progress logging
            Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            int step = 10; // Step in percent
            for (int i = step; i <= 100; i += step) {
                map.put(knowledgeArtifacts.size() * i / 100, i);
            }

            Model referenceModel = tdb.getNamedModel(referenceModelName);
            // Remove previous data from the model
            tdb.execute(() -> {
                long size = referenceModel.size();
                if (size != 0) {
                    logger.info("Removed " + size + " entries from the \"" + referenceModelName + "\" model");
                    referenceModel.removeAll();
                }
            });

            Property referenceProperty = referenceModel.createProperty(ontologyIri + "#" + referencePredicate);
            logger.info("Started to enrich knowledge artifacts, please have patience...");

            int index = 0;
            for (RDFNode artifact : knowledgeArtifacts.keySet()) {
                for (RDFNode resource : contentResources.keySet()) {
                    if (knowledgeArtifacts.get(artifact).toLowerCase().contains(contentResources.get(resource).toLowerCase())) {
                        // Add a new triple if content resource is found in the content of a knowledge artifact
                        tdb.executeWrite(() -> referenceModel.add(artifact.asResource(), referenceProperty, resource));
                    }
                }

                index++;
                if (map.keySet().contains(index)) {
                    logger.info("Processed " + map.get(index) + "% of all knowledge artifacts");
                }
            }

            tdb.executeRead(() -> logger.info("Added " + referenceModel.size() + " entries to the \"" + referenceModelName + "\" model"));
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
     * @param ontologyIri - the ontology IRI for knowledge artifacts
     * @param titlePredicate - the predicate name for the title of the content nodes
     * @return the {@link Map} with subject nodes and their names
     */
    private Map<RDFNode, String> retrieveContentResources(Model contentModel, URL ontologyIri, String titlePredicate) {

        Map<RDFNode, String> contentResources = new HashMap<RDFNode, String>();
        String query = "SELECT DISTINCT ?subject WHERE {?subject ?predicate ?object}";

        try (QueryExecution qExec = QueryExecution.model(contentModel).query(query).build()) {
            ResultSet result = qExec.execSelect();
            while (result.hasNext()) {
                QuerySolution solution = result.nextSolution();
                RDFNode subjectNode = solution.get("subject");
                StmtIterator iterator = subjectNode.asResource().listProperties(contentModel.getProperty(ontologyIri + "#" + titlePredicate));

                while (iterator.hasNext()) {
                    Statement statement = iterator.next();
                    if (statement != null) {
                        String subjectLiteral = statement.getObject().asLiteral().toString();
                        contentResources.put(subjectNode, subjectLiteral);
                    }
                }
            }
        }

        logger.info("Retrieved " + contentResources.size() + " literals for content resources to look for");
        return contentResources;
    }

    /**
     * Retrieves all subject nodes from {@code knowledgeModel} that have a predicate 
     * {@code ontologyIri#contentPredicate} mapped to the corresponding object parsed as visible text
     * @param knowledgeModel - the {@link Model} with knowledge artifacts
     * @param ontologyIri - the ontology IRI for knowledge artifacts
     * @param contentPredicate - the predicate name of knowledge artifacts' content
     * @param titlePredicate - the predicate name for the title of the content nodes
     * @return the {@link Map} with subject nodes and their content as plain text
     */
    private Map<RDFNode, String> retrieveKnowledgeArtifacts(Model knowledgeModel, URL ontologyIri, String contentPredicate, String titlePredicate) {

        Map<RDFNode, String> knowledgeArtifacts = new HashMap<RDFNode, String>();
        String query = "PREFIX predicate: <" + ontologyIri + "#> "
            + "SELECT ?subject ?content ?title "
            + "WHERE {?subject predicate:" + titlePredicate + " ?title . "
            + "OPTIONAL {?subject predicate:" + contentPredicate + " ?content } }";

        try (QueryExecution qExec = QueryExecution.model(knowledgeModel).query(query).build()) {
            ResultSet result = qExec.execSelect();
            while (result.hasNext()) {
                QuerySolution solution = result.nextSolution();
                RDFNode subjectNode = solution.get("subject");

                RDFNode contentNode = solution.get("content");
                String titleString = solution.get("title").toString();
                String objectContentText = contentNode != null ? Jsoup.parse(contentNode.asLiteral().toString()).text() : "";

                knowledgeArtifacts.put(subjectNode, titleString + " || " + objectContentText);
            }
        }

        logger.info("Retrieved " + knowledgeArtifacts.size() + " knowledge artifacts to enrich");
        return knowledgeArtifacts;
    }

}