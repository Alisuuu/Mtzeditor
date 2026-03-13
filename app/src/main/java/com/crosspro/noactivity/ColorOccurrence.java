package com.crosspro.noactivity;

import java.io.Serializable;

public class ColorOccurrence implements Serializable {
    public String filePath;
    public int lineNumber;
    public String lineContent;
    public String attributeName;
    public String entryName; // For colors inside nested zip modules

    public ColorOccurrence(String filePath, int lineNumber, String lineContent, String attributeName, String entryName) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.lineContent = lineContent;
        this.attributeName = attributeName;
        this.entryName = entryName;
    }
}
