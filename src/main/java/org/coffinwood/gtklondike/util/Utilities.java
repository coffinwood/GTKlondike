package org.coffinwood.gtklondike.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * loads {@link Attribution} entries from the "/attributions.xml" classpath resource and
 * some other minor stuff that would litter the other classes
 */
public final class Utilities {


    /**
     * parse every {@code <work>} entry out of "/attributions.xml", skipping entries with a blank
     * {@code <name>} so the unfilled template ships without producing empty credits
     * @return attributions in document order
     */
    public static List<Attribution> loadAttributions() {
        List<Attribution> attributions = new ArrayList<>();
        try(InputStream inputStream = Utilities.class.getResourceAsStream("/attributions.xml")) {
            if(inputStream == null) {
                throw new IllegalStateException("Attributions could not be loaded");
            }
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(inputStream);

            NodeList workNodes = document.getElementsByTagName("work");
            for(int index = 0; index < workNodes.getLength(); index++) {
                Element work = (Element) workNodes.item(index);
                String name = getAttributionChildText(work, "name");
                // skip empty records
                if(name.isEmpty()) {
                    continue;
                }
                attributions.add(new Attribution(
                        name,
                        getAttributionChildText(work, "author"),
                        getAttributionChildText(work, "website"),
                        getAttributionChildText(work, "license"),
                        getAttributionChildText(work, "notes")));
            }
        }
        catch(IOException | javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException exception) {
            throw new IllegalStateException("Could not read attributions.xml.", exception);
        }
        return attributions;
    }


    /**
     * text content of the first child element with the given tag name, trimmed line-by-line so
     * a multi-line entry in the XML (indented to stay readable in the source) doesn't carry that
     * indentation into the rendered text
     * @param parent parent element
     * @param tagName child tag name
     * @return trimmed text content, or "" if the child is missing/empty
     */
    private static String getAttributionChildText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if(children.getLength() == 0) {
            return "";
        }
        Node node = children.item(0);
        String text = node.getTextContent();
        if(text == null) {
            return "";
        }
        return Arrays.stream(text.split("\n"))
                .map(String::trim)
                .collect(Collectors.joining("\n"))
                .trim();
    }


    /**
     * format a third party {@link Attribution} as a single credit-section line.
     * @param attribution work being credited
     * @return formatted line
     */
    public static String formatAttribution(Attribution attribution) {
        // Name / category
        StringBuilder label = new StringBuilder(Utilities.escapeMarkup(attribution.name()));

        // Author(s)
        if(! attribution.author().isEmpty()) {
            label.append(" by ").append(Utilities.escapeMarkup(attribution.author()));
        }

        // Website URL
        if(! attribution.website().isEmpty()) {
            label.append(" - <a href=\"").append(Utilities.
                    escapeMarkup(attribution.website())).append("\"><span>").append("[Website]").append("</span></a>");
        }

        // Licence (as URL)
        if(! attribution.license().isEmpty()) {
            label.append(" - ");
            String tempText = Utilities.escapeMarkup(attribution.license());
            if(tempText.toLowerCase().startsWith("https://")) {
                label.append("<a href=\"").append(tempText).append("\"><span>").
                        append("[Licence]").append("</span></a>");
            }
            else {
                label.append(tempText);
            }
        }

        // Notes
        if(! attribution.notes().isEmpty()) {
            // Pango markup has no <br/> tag; a real newline is what actually breaks the line,
            // an invalid tag instead makes the whole label fail to parse and fall back to
            // showing its raw, un-rendered markup source (the literal "<span " the tag left behind)
            label.append('\n').append(Utilities.escapeMarkup(attribution.notes()));
        }

        return label.toString();
    }


    /**
     * escape characters just to be safe
     * @param text raw text
     * @return "safe" text
     */
    public static String escapeMarkup(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }


    /**
     * text content of the "/LICENCE" classpath resource
     * @return licence text, or a fallback message if the resource is missing/unreadable
     */
    public static String loadLicenseText() {
        return loadResourceText("/LICENCE");
    }


    /**
     * text content of a UTF-8 classpath resource, read via a stream rather than resolving a
     * filesystem path. This is what makes it work from inside a packed (shadow) jar too, where
     * a resource URL's "file" part doesn't correspond to a real path on disk
     * @param resourcePath classpath resource path, e.g. "/style/gtklondike.css"
     * @return resource text, or "" if the resource doesn't exist
     */
    public static String loadResourceText(String resourcePath) {
        try(InputStream inputStream = Utilities.class.getResourceAsStream(resourcePath)) {
            if(inputStream == null) {
                return "";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch(IOException exception) {
            throw new IllegalStateException("Could not read " + resourcePath, exception);
        }
    }


    /**
     * scan the stylesheet for every CSS class selector starting with "bg_" (e.g. ".bg_trees"),
     * so the Preferences background picker always matches what's actually in the stylesheet
     * @param cssText the same stylesheet text already loaded into the CssProvider
     * @return names of the discovered classes (without the leading '.'), in first-seen order
     */
    public static List<String> discoverCssBackgroundClasses(String cssText) {
        Matcher matcher = Pattern.compile("\\.bg_[\\w-]+").matcher(cssText);
        List<String> names = new ArrayList<>();
        while(matcher.find()) {
            names.add(matcher.group().substring(1)); // drop the leading '.'
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }
}
