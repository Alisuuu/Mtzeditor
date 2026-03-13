package com.crosspro.noactivity;

import java.io.Serializable;

public class ThemeComponent implements Serializable {
    public String name;
    public String path;

    public ThemeComponent(String name, String path) {
        this.name = name;
        this.path = path;
    }
}
