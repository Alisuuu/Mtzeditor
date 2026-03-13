package com.crosspro.noactivity;

public class ResourceEntry {
    public String type; // color, dimen, bool, integer, etc.
    public String name;
    public String value;
    public String pkg; // Optional package attribute

    public ResourceEntry(String type, String name, String value, String pkg) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.pkg = pkg;
    }
}
