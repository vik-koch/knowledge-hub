package com.khub;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collector;

import com.khub.common.Configuration;
import com.khub.common.DockerRunner;
import com.khub.common.MongoConnector;
import com.khub.common.PipelineStep;
import com.khub.common.ResourceProvider;
import com.khub.crawling.ConfluenceCrawler;
import com.khub.crawling.TeamsCrawler;
import com.khub.enriching.KnowledgeEnricher;
import com.khub.exporting.MongoExporter;
import com.khub.extracting.ContentExtractor;
import com.khub.importing.TDBImporter;
import com.khub.mapping.RMLMapper;
import com.khub.processing.JSONProcessor;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

public class AppRunner {

    private static final Logger logger = Logger.getLogger(AppRunner.class.getName());

    // Pipeline steps iterator
    private Iterator<PipelineStep> steps;

    private enum PipelineResult {
        FINISHED,
        FAILED
    }

    private Configuration config;
    private MongoClient mongoClient;
    private Process docker;

    private boolean runAll = true;

    private String rawDataSuffix = "_raw_data";
    private String processedDataSuffix = "_processed";

    /**
     * Recursively runs the pipeline with steps defined in {@code steps} iterator
     * @param currentState - the current {@link PipelineStep} to run
     * @return the {@link PipelineResult} after pipeline execution
     */
    private PipelineResult runPipeline(PipelineStep currentState) {

        String currentStepName = currentState.toString();

        Instant startTime = Instant.now();
        logOnStepStart(currentStepName);

        boolean stepResult = switch(currentState) {

            case KNOWLEDGE_CRAWLING         -> prepareMongoDb() && crawlKnowledge();
            case KNOWLEDGE_PROCESSING       -> prepareMongoDb() && processKnowledge();
            case KNOWLEDGE_EXPORTING        -> prepareMongoDb() && exportKnowledge();
            case KNOWLEDGE_MAPPING          -> mapKnowledge();
            case KNOWLEDGE_IMPORTING        -> importKnowledge();
            case ONTOLOGY_IMPORTING         -> importOntology();
            case CONTENT_EXTRACTING         -> extractContent();
            case CONTENT_MAPPING            -> mapContent();
            case CONTENT_IMPORTING          -> importContent();
            case KNOWLEDGE_GRAPH_ENRICHING  -> enrichKnowledgeGraph();

        };

        Duration stepDuration = Duration.between(startTime, Instant.now());

        // A step cannot take more than an hour
        if (stepDuration.toHoursPart() > 0) System.exit(1);
        
        String stepDurationString = stepDuration.toMinutesPart() + " minutes and " + stepDuration.toSecondsPart() + " seconds";
        logOnStepFinish(currentStepName, stepDurationString, stepResult);

        if (stepResult) {
            if (steps.hasNext() && runAll) {
                return runPipeline(steps.next());
            } else {
                return PipelineResult.FINISHED;
            }
        } else {
            return PipelineResult.FAILED;
        }
    }

    /**
     * Prepares the Mongo DB instance for saving retrieved data in
     * @return true, if a running {@link MongoClient} could be found
     */
    private boolean prepareMongoDb() {
        // Start Docker Compose
        if (mongoClient == null && (docker == null || !docker.isAlive())) {
            docker = DockerRunner.startDockerCompose(config.dockerPath);
        }

        // Start MongoDB client
        if (mongoClient == null) {
            try {
                mongoClient = MongoConnector.getClient(config.mongoConnectionString);
            } catch (MongoException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Disposes the active docker-compose process by stopping the container
     */
    private void shutdownDocker() {
        docker.destroy();
        DockerRunner.stopDockerCompose(config.dockerPath);
    }

    /**
     * Returns the current date in the {@code YYYY-MM-DD} format
     * @return the formatted current {@link LocalDate}
     */
    private String getCurrentDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate localDate = LocalDate.now();
        return (formatter.format(localDate));
    }

    /**
     * Logs initial information upon starting the step execution
     * @param stepName - the name of a {@link PipelineStep}
     */
    private void logOnStepStart(String stepName) {
        System.out.println();
        logger.info("-> Starting the " + stepName + " step...");
    }

    /**
     * Logs final information upon finishing the step execution
     * @param stepName - the name of a {@link PipelineStep}
     * @param duration - the duration of thep step
     * @param result - true, if the step runned successfully, false otherwise
     */
    private void logOnStepFinish(String stepName, String duration, boolean result) {
        if (result == true) {
            logger.info("# The " + stepName + " step is successfully finished in " + duration);
        } else {
            logger.severe("# The " + stepName + " step failed");
        }
    }

    /**
     * Runs the {@link com.khub.crawling.AbstractCrawler AbstractCrawler}
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean crawlKnowledge() {
        String databaseName = getCurrentDate() + rawDataSuffix;
        MongoDatabase database = mongoClient.getDatabase(databaseName);

        boolean confluenceResult = false;
        boolean teamsResult = false;

        // Confluence Crawling
        if (config.confluenceEndpoint != null && config.confluenceHeader.isValid()) {
            logger.info("Confluence API configuration is provided, proceeding to Confluence crawling...");
            ConfluenceCrawler confluenceCrawler = new ConfluenceCrawler(config.confluenceEndpoint, config.confluenceHeader);
            confluenceResult = confluenceCrawler.run(database);

            logger.info("Confluence data was successfully crawled");
        } else {
            logger.warning("Unable to crawl Confluence data because endpoint or header configuration is empty");
        }

        // MS Teams Crawling
        if (config.teamsEndpoint != null && config.teamsHeader.isValid()) {
            logger.info("MS Teams API configuration is provided, proceeding to Teams crawling...");
            TeamsCrawler teamsCrawler = new TeamsCrawler(config.teamsEndpoint, config.teamsHeader);
            teamsResult = teamsCrawler.run(database);

            logger.info("Teams data was successfully crawled");
        } else {
            logger.warning("Unable to crawl Teams data because endpoint or header configuration is empty");
        }

        if (runAll == false) shutdownDocker();
        return (confluenceResult || teamsResult);
    }

    /**
     * Runs the {@link com.khub.processing.JSONProcessor JSONProcessor}
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean processKnowledge() {
        String sourceDatabaseName = getCurrentDate() + rawDataSuffix;
        MongoDatabase sourceDatabase = mongoClient.getDatabase(sourceDatabaseName);

        String outputDatabaseName = getCurrentDate() + processedDataSuffix;
        MongoDatabase outputDatabase = mongoClient.getDatabase(outputDatabaseName);

        JSONProcessor processor = JSONProcessor.of(config.processingPath, config.confluenceEndpoint);
        boolean result = processor.run(sourceDatabase, outputDatabase);

        if (runAll == false) shutdownDocker();
        return result;
    }

    /**
     * Runs the {@link com.khub.exporting.MongoExporter MongoExporter}
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean exportKnowledge() {
        String databaseName = getCurrentDate() + processedDataSuffix;
        MongoDatabase database = mongoClient.getDatabase(databaseName);

        MongoExporter exporter = new MongoExporter();
        boolean result = exporter.run(database, config.knowledgePath, "source");

        shutdownDocker();
        return result;
    }

    /**
     * Runs the {@link com.khub.mapping.RMLMapper RMLMapper} for knowledge mapping
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean mapKnowledge() {
        RMLMapper mapper = new RMLMapper(config.dockerPath);
        return mapper.run(config.knowledgePath, "output");
    }

    /**
     * Runs the {@link com.khub.importing.TDBImporter TDBImporter} for knowledge importing
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean importKnowledge() {
        TDBImporter importer = TDBImporter.of(config.tdbPath);
        return importer != null && importer.importRDF(config.knowledgePath, config.knowledgeModelName);
    }

    /**
     * Runs the {@link com.khub.importing.TDBImporter TDBImporter} for ontology importing
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean importOntology() {
        TDBImporter importer = TDBImporter.of(config.tdbPath);
        return importer != null && importer.importOWL(config.ontologyPath, config.ontologyModelName);
    }

    /**
     * Runs the {@link com.khub.extracting.ContentExtractor ContentExtractor} for content extracting
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean extractContent() {
        ContentExtractor extractor = ContentExtractor.of(config.tdbPath);
        return extractor != null && extractor.run(config.contentPath, config.queriesPath, "source", config.contentPredicate);
    }

    /**
     * Runs the {@link com.khub.mapping.RMLMapper RMLMapper} for content mapping
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean mapContent() {
        RMLMapper mapper = new RMLMapper(config.dockerPath);
        return mapper.run(config.contentPath, "output");
    }

    /**
     * Runs the {@link com.khub.importing.TDBImporter TDBImporter} for content importing
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean importContent() {
        TDBImporter importer = TDBImporter.of(config.tdbPath);
        return importer != null && importer.importRDF(config.contentPath, config.contentModelName);
    }

    /**
     * Runs the {@link com.khub.enriching.KnowledgeEnricher KnowledgeEnricher}
     * @param result - true, if the step runned successfully, false otherwise
     */
    private boolean enrichKnowledgeGraph() {
        KnowledgeEnricher enricher = KnowledgeEnricher.of(config.tdbPath);
        return enricher != null && enricher.run(config.contentModelName, config.knowledgeModelName, 
            config.referenceModelName, config.ontologyIri, config.contentPredicate, config.titlePredicate, config.referencePredicate);
    }

    /**
     * Runs the application from the console input
     * @param args - the console arguments
     */
    private void run(String[] args) {
        Instant startTime = Instant.now();

        // Prepare data for the console operation
        Map<String, PipelineStep> stepMap = new HashMap<String, PipelineStep>();
        List<String> stepHelpInfo = new ArrayList<String>();
        List<PipelineStep> stepList = Arrays.asList(PipelineStep.values());
        for (PipelineStep step : stepList) {
            List<String> stepName = List.of(step.name().split("_"));
            String stepInitials = stepName.stream()
                .map(s -> s.charAt(0))
                .collect(Collector.of(StringBuilder::new, StringBuilder::append,
                         StringBuilder::append, StringBuilder::toString));
            stepMap.put(stepInitials, step);
            stepHelpInfo.add(stepList.indexOf(step) + 1 + ". " + stepInitials + " (" + step + ")");
        }

        PipelineStep currentStep = null;
        boolean wrongStepName = false;

        // Check arguments
        switch(args.length) {

            case 3:
                if (args[2].equals("--only")) {
                    runAll = false;
                }
                else {
                    break;
                }

            case 2:
                if (args[0].equals("--run")) {
                    currentStep = stepMap.get(args[1].toUpperCase());
                    wrongStepName = currentStep == null ? true : false;
                }
                break;

            case 1:
                if (args[0].equals("--help")) {
                    System.out.println("khub [--run STEP] [--only]\n" + 
                        "  --run          Start the app from the given step (use only step initials)\n" + 
                        "  --only         Run only the given step\n" + 
                        "  --help\n" + 
                        "  --version\n\n" + 
                        "Available pipeline steps:\n  " + String.join("\n  ", stepHelpInfo));
                    System.exit(0);
                }

                if (args[0].equals("--version")) {
                    try {
                        Properties appProperties = new Properties();
                        appProperties.load(AppRunner.class.getResourceAsStream("/application.properties"));
                        System.out.println("Version: " + appProperties.getProperty("version"));
                    } catch (Exception e) {
                        System.out.println("Unable to retrieve version");
                    }
                    System.exit(0);
                }

                break;
            
            case 0:
                currentStep = stepList.get(0);
                break;
        }

        if (wrongStepName) {
            System.out.println("Wrong pipeline step \"" + args[1] + 
                "\" given, available steps:\n  " + String.join("\n  ", stepHelpInfo));
            System.exit(1);
        }
        else if (currentStep == null) {
            System.out.println("Unknown input, type --help for help");
            System.exit(1);
        }

        // Initialise configuration
        Path configPath = Paths.get("./configuration/builder-config.properties");
        Properties properties = ResourceProvider.loadProperties(configPath);

        if (properties == null) System.exit(1);
        config = new Configuration(properties);

        // Set iterators for the next steps
        steps = stepList.subList(stepList.indexOf(currentStep) + 1, stepList.size()).iterator();

        // Runs pipeline
        PipelineResult result = runPipeline(currentStep);

        Duration stepDuration = Duration.between(startTime, Instant.now());
        String stepDurationString = stepDuration.toMinutesPart() + " minutes and " + stepDuration.toSecondsPart() + " seconds";

        if (result == PipelineResult.FAILED) {
            logger.info("Finished due to an error in " + stepDurationString);
            System.exit(1);
        }

        logger.info("Finished with success in " + stepDurationString);
        System.exit(0);
    }

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] %4$s: %5$s %n");
        AppRunner app = new AppRunner();
        app.run(args);
    }

}