package com.khub.jena;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;

import com.khub.exceptions.InvalidConfigurationException;
import com.khub.misc.Configuration;

public class KnowledgeGraphEnricher {

    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    public void enrich(Properties configuration) {
        try {
            Dataset tdb = TDBHandler.getDataset(configuration);
            String khub = "http://semanticweb.org/khub/ontologies/2022/11/ontology#";

            Map<RDFNode, String> instances = new HashMap<RDFNode, String>();

            // Gets all instances to be searched for
            tdb.begin(ReadWrite.READ);
            Model contentModel = tdb.getNamedModel("content");
            try (QueryExecution qExec = QueryExecution.model(contentModel).query("SELECT DISTINCT ?x WHERE {?x ?y ?z}").build()) {
                ResultSet result = qExec.execSelect();
                while (result.hasNext()) {
                    QuerySolution solution = result.nextSolution();
                    RDFNode node = solution.get("x");
                    String instance = node.toString();

                    instances.put(node, instance.substring(instance.lastIndexOf("#") + 1, instance.length()));
                }
            }
            tdb.end();

            Map<RDFNode, String> artifacts = new HashMap<RDFNode, String>();

            // Gets all artifacts with subject and content
            tdb.begin(ReadWrite.READ);
            Model knowledgeModel = tdb.getNamedModel("knowledge");
            try (QueryExecution qExec = QueryExecution.model(knowledgeModel).query("PREFIX khub: <" + khub + "> SELECT DISTINCT ?x ?y WHERE {?x khub:content ?y}").build()) {
                ResultSet result = qExec.execSelect();
                while (result.hasNext()) {
                    QuerySolution solution = result.nextSolution();
                    RDFNode instance = solution.get("x");
                    String content = solution.get("y").toString();
                    artifacts.put(instance, content);
                    // artifacts[instance] = content;
                    // instances.add(instance.substring(instance.lastIndexOf("#") + 1, instance.length()));
                }
            }
            tdb.end();

            // Adds new label triples to labels model
            tdb.begin(ReadWrite.WRITE);
            Model labelModel = tdb.getNamedModel("labels");
            Property property = labelModel.createProperty(khub, "label");
            for (RDFNode artifact : artifacts.keySet()) {
                for (RDFNode instance : instances.keySet()) {
                    if (artifacts.get(artifact).contains(instances.get(instance))) {
                        labelModel.add(artifact.asResource(), property, instance);
                    }
                }
            }
            tdb.commit();
            tdb.end();

        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Invalid configuration provided", e);
            return;
        }        
    }

    public static void main(String[] args) throws InvalidConfigurationException {
        String configPath = String.join(File.separator, "knowledge-hub", "src", "main", "resources", "config.properties");
        Properties configuration = Configuration.initialize(configPath);
        KnowledgeGraphEnricher kge = new KnowledgeGraphEnricher();
        kge.enrich(configuration);
    }
}
