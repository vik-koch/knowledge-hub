# Knowledge Hub

[![Knowledge Hub CI](https://github.com/vik-koch/knowledge-hub/actions/workflows/knowledge-hub.yml/badge.svg)](https://github.com/vik-koch/knowledge-hub/actions/workflows/knowledge-hub.yml)

Knowledge Hub is a semantic search application for enterprise knowledge based on a enterprise knowledge graph. It consists of 2 parts:

1. [KHub Builder](/khub-builder/README.md) - a pipeline for automatic construction of knowledge graphs based on [Apache Jena](https://github.com/apache/jena) framework
2. [KHub Explorer](/khub-explorer/README.md) - an overlay search application for interacting with Apache Fuseki via full-text search

**Enterprise knowledge** includes company internal data distributed across multiple platforms such as Atlassian Confluence, Microsoft SharePoint/Teams, Slack, Notion, etc. This data is present in form of messages, Wiki pages, documents and can be integrated in one common system for searching, analyzing and other purposes.

## Requirements
1. Java SE 17 or higher
2. Docker (Desktop / Engine) with Docker Engine 19.03.0 or higher 

## How to Run
1. Create Access Token(s):
    * Confluence API Token (see [link](https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account/)). The token is connected to an account and has the form `me@example.com:my-api-token`. For crawling open Confluence groups no token is required, use `null` as `confluence.token`.
    * Microsoft Graph API Token (see [link](https://learn.microsoft.com/en-us/graph/auth-v2-user)). The app must be registered in Azure AD. The user needs to be member of all MS Teams that should be processed. The following permissions are also required:
        * Team.ReadBasic.All
        * Channel.ReadBasic.All
        * ChannelMessage.Read.All
2. Fill in the `confluence.endpoint`, `confluence.token` and `teams.token` in the KHub Builder configuration file. For Confluence Cloud use `https://your-domain.atlassian.net/wiki/`, for Confluence Server - `http://your-domain.com/`, the application will prepend the second part of the endpoint starting with `rest/api/...`. Use tokens from 1. or `null` for sending crawling requests without authentication header. Make other configuration adjustments if needed.
3. (Optional) Define queries and mapping files for additional knowledge extraction, see examples in [Knowledge Graph Enriching](/khub-builder/README.md#3-knowledge-graph-enriching).
4. Run the pipeline completely or only its separate steps with `java -jar khub-builder.jar`, see additional commands with `--help`. After successful run, several named graphs are constructed and saved in Jena TDB in the directory configured in `tdb.path`.
5. Run `start-khub-explorer.bat` or `docker-compose up` in the explorer directory for starting Jena.TextIndexer, Fuseki Server and KHub Explorer. If needed, [Fuseki assembler file](/khub-explorer/config/explorer-config.ttl) and [docker-compose.yaml](/khub-explorer/docker-compose.yaml) can be modified. The KHub Explorer will start locally allowing full-text search on the constructed knowledge graph.

## Dependencies
| Dependency | Version | License |
| - | - | - | 
| [Apache Maven](https://github.com/apache/maven) | 3.9.0 | Apache 2.0 |
| [Apache Jena](https://github.com/apache/jena) | 4.7.0 | Apache 2.0 |
| [Bootstrap](https://github.com/twbs/bootstrap) | 5.2.3 | MIT |
| [Google Gson](https://github.com/google/gson) | 2.10.1 | Apache 2.0 |
| [Jsoup](https://github.com/jhy/jsoup) | 1.15.3 | MIT |
| [MongoDB](https://github.com/mongodb/mongo) | latest-image | [License](https://github.com/mongodb/mongo/blob/master/LICENSE-Community.txt) |
| [MongoDB Java Driver](https://github.com/mongodb/mongo-java-driver) | 3.12.11 | Apache 2.0 |
| [React](https://github.com/facebook/react) | 18.2.0 | MIT |
| [React Bootstrap](https://github.com/react-bootstrap/react-bootstrap) | 2.7.2 | MIT |
| [React Scripts](https://github.com/facebook/create-react-app) | 5.0.1 | MIT |
| [RML Mapper](https://github.com/RMLio/rmlmapper-java) | latest-image | MIT |