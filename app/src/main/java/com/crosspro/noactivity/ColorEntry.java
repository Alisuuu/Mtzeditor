package com.crosspro.noactivity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ColorEntry implements Serializable {
    public String hexValue; // e.g., #FFFFFF or #FFFFFFFF
    public String category; // "Texto", "Ícones", "Áreas", "Outros"
    public List<ColorOccurrence> occurrences = new ArrayList<>();

    public ColorEntry(String hexValue, String category) {
        this.hexValue = hexValue.toUpperCase();
        this.category = category;
    }

    public int getCount() {
        return occurrences.size();
    }
}
