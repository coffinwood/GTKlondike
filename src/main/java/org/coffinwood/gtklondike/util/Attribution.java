package org.coffinwood.gtklondike.util;


/**
 * a single third-party work (card art, icon, background, etc.) credited in the About
 * dialogue's "Third-Party Assets" credit section
 * @param name work's name
 * @param author work's author/creator
 * @param website URL to the work's source/homepage, or "" if none
 * @param license licence or usage terms (e.g. "CC0-1.0", "CC-BY 4.0", "Public Domain")
 * @param notes any additional remarks, or "" if none
 */
public record Attribution(String name, String author, String website, String license, String notes) {}
