## KHub Builder
KHub Builder is used for crawling, processing and mapping of semi-structured data currently from Atlassian Confluence and Microsoft Teams (to be extended). It is a **Java** application for running multiple steps which are executed locally or in Docker environment w/o any other dependencies. The pipeline is split in the following parts:
1. Crawling and metadata processing
2. RDF mapping and saving data in Jena TDB
3. Knowledge graph enriching (optional)

#### 1. Crawling and Processing

Currently supported data entities:

| Confluence | Teams |
| - | - |
| Users (with personal Pages), Spaces, Pages, Commentaries | Teams, Channels, Posts |

Each knowledge artifact has its own metadata properties, which are unified in [processing.json](/resources/processing.json) defined as key-value pairs. The key is the resulting name and the value array define the previous namings from the sources as JSON paths. The pipeline can crawl either of the sources or both depending on the [configuration](/khub-builder/src/main/resources/builder-config.properties).

Data is temporarily saved in a [MongoDB](https://www.mongodb.com/) instance in a Docker container and then exported as JSON.

#### 2. Mapping and Persisting
Resulted JSON is mapped to RDF via [RMLMapper](https://github.com/RMLio/rmlmapper-java) hosted in a Docker container. The mapping files are defined in [resources](/resources/knowledge/) and can be easily modified. Mapped entities are imported into a named graph (model) in a local [Jena TDB2](https://jena.apache.org/documentation/tdb2/) storage.

The mapped data is annotated in the defined [ontology](/resources/ontology/khub.owl) with classes, object and data properties. The main inference principles are parent-child class hierarchy and **class transitivity**. It allows using context from ancestor/descendant entities in the knowledge hierarchy for searching.

Currently supported object and data properties:

| Property | Description |
| - | - |
| ancestor | Denotes the parent node (superordinate channel, team, space, etc.) of a knowledge artifact |
| author | Denotes the author of a knowledge artifact |
| reference | Denotes the textual reference to another node *(see [3. Knowledge Graph Enriching](#3-knowledge-graph-enriching))* |
| content | The content of the knowledge artifact |
| creationTime | The creation time of a knowledge artifact |
| email | The e-mail address of a person |
| lastUpdateTime | The last update time of a knowledge artifact |
| link | The source URL to the searchable content |
| title | The title of an instance (e.g. knowledge artifact) |

A simple knowledge graph is constructed after this step that can optionally be enriched with additional entities and semantics.

#### 3. Knowledge Graph Enriching
This step involves:
* Querying the constructed graph with SPARQL
* Parsing the `content` literals and exporting as JSON
* RDF mapping with RMLMapper
* Iterating over the constructed graph creating new `reference` triples
* Importing the user's custom ontology(-ies)

SPARQL queries must be put in the folder specified in the `queries.path` in [configuration](/khub-builder/src/main/resources/builder-config.properties) in text format with any extension, here is a simple example:

```sparql
PREFIX khub: <http://semanticweb.org/ontologies/khub#>

SELECT ?content
WHERE {
	khub:123456789 khub:content ?content .
}
```

Multiple variables can be used in `SELECT` clause. In order to parse `content` literal, the variable name in the query must correspond to the `content.predicate` in the [configuration](/khub-builder/src/main/resources/builder-config.properties). Every HTML table is parsed and extracted from the `content` literal, which results in the following JSON format:

```json
{
  "Result_#": {
    "Table_#": {
      "Row_#": "Value",
      "Key": "Value" 
    },
    "Variable": "Value",
  },
}
```

* `#` is an enumerator
* `Table_#` is optional if the `content` literal is present and contains HTML tables
* If table has headers, it is mapped as key-value pairs, otherwise a `Row_#` placeholder is used
* Variables correspond to other query variables besides `content`

These JSON files are mapped with mapping files defined in `content.path` similarly to the present mapping files *(see [2. Mapping and Persisting](#2-mapping-and-persisting))*. For accessing the table values the `$.*.*[*]` iterator can be used.

After mapping the new entities are imported into Jena TDB. Custom ontologies can be used for more semantics.

The `title` and `content` literals of the main graph are traversed to find textual occurrences of `title` literals in newly extracted data. New triples of the form **`khub:123456789 khub:reference iri:value`** are created supporting custom IRIs for other ontologies.

#### Named graphs

| Name | Configuration Key | Description |
| - | - | - |
| knowledge | knowledge.model.name | Main graph with knowledge artifacts *(see [2. Mapping and Persisting](#2-mapping-and-persisting))* |
| ontology  | ontology.model.name  | Graph including **KHub** and other custom ontologies specified in `ontology.path` |
| content   | content.model.name   | Graph with new derived entities mainly from tables *(see [3. Knowledge Graph Enriching](#3-knowledge-graph-enriching))* |
| reference | reference.model.name | Graph with `reference` predicates |