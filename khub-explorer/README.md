## KHub Explorer
KHub Explorer is used as an interface between full-text search Web UI and Apache Fuseki server operating with SPARQL. It provides a text index with [Jena.TextIndexer](https://jena.apache.org/documentation/query/text-query.html) and a dockerized bundle with Fuseki and search application. KHub Explorer can be split into the following components:
1. Text index and SPARQL server
2. Overlay search application

#### 1. Text Index and SPARQL Server
The text index and Apache Fuseki server configuration is defined in [the assembler file](/khub-explorer/config/explorer-config.ttl). The indexed entities include:
* `khub:title`
* `rdfs:label`

All named graphs in the given TDB2 store are joined. The `OWLMicroFBRuleReasoner` reasoner is applied for inference, which allows querying parent-child relationships for the `reference` predicate. For example, if a custom ontology defines a class hierarchy with Class1 -> Class2 -> Class3 and a knowledge artifact references an instance of Class3, it will be found, if the RDF label of Class1 or Class2 is part of the search query.

When querying the graph, the weight of the text score of all ancestor and descendant nodes is part of the final result. The following formula is used: 
```sparql
SELECT ?result (?score 
            + 2.5 * COALESCE(AVG(?refScore), 0) 
            + 0.5 * COALESCE(AVG(?parentScore), 0)  
            + 0.5 * COALESCE(AVG(?childScore), 0) AS ?totalScore)
```
See [the query template](/khub-explorer/public/template.sparql) for more information.

#### 2. Search Application
KHub Explorer is a dockerized **React** application that can communicate with any Fuseki server defined in [docker-compose.yaml](/khub-explorer/docker-compose.yaml). Query speed depends on the size of the dataset and the available computing resources. There is currently a known bug where the first query to the Fuseki server may take up to 2-3 minutes.  

