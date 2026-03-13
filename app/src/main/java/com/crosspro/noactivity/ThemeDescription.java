package com.crosspro.noactivity;

import java.util.HashMap;
import java.util.Map;

public class ThemeDescription {
    public String title;
    public String author;
    public String designer;
    public String version;
    public String uiVersion;
    public String miuiAdapterVersion;
    public String description;
    public Map<String, String> localizedTitles = new HashMap<>();
    public Map<String, String> localizedAuthors = new HashMap<>();
    public Map<String, String> localizedDesigners = new HashMap<>();
    public Map<String, String> localizedDescriptions = new HashMap<>();
}
