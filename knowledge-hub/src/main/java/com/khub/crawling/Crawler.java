package com.khub.crawling;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

public interface Crawler {
    
    public Map<String, List<JsonElement>> run();

}