package com.vagell.smartaphone;

import android.graphics.Color;

public class RGBColor {
    public int r = 0;
    public int g = 0;
    public int b = 0;

    public int toIntColor() {
        return Color.rgb(r, g, b);
    }

    public String toString() {
        return "R: " + r + " G: " + g + " B: " + b;
    }
}
