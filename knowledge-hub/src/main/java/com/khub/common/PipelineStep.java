package com.khub.common;

public enum PipelineStep {

    KNOWLEDGE_CRAWLING      ("Knowledge Crawling"),
    KNOWLEDGE_PROCESSING    ("Knowledge Processing"),

    KNOWLEDGE_EXPORTING     ("Knowledge Exporting"),
    KNOWLEDGE_MAPPING       ("Knowledge Mapping"),
    KNOWLEDGE_IMPORTING     ("Knowledge Importing"),

    ONTOLOGY_IMPORTING      ("Ontology Importing"),

    CONTENT_EXTRACTING      ("Content Extracting"),
    CONTENT_MAPPING         ("Content Mapping"),
    CONTENT_IMPORTING       ("Content Importing"),

    KNOWLEDGE_ENRICHING     ("Knowledge Enriching");

    private final String name;

    private PipelineStep(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

}