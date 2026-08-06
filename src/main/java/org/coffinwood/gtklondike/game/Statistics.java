package org.coffinwood.gtklondike.game;

import org.gnome.glib.GLib;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;


/**
 * a simple game statistics tracking class
 */
public class Statistics {
    private static int gamesPlayed, gamesWon, moves;
    // where loadStats()/saveStats() read/write; resolved once by loadStats() from GLib's
    // per-user data dir, e.g. ~/.local/share/gtklondike/gamestats.xml
    private static Path statsFile;


    /**
     * load saved stats from the user's writable gamestats.xml - or, the first time the app runs,
     * before that file exists yet, leave gamesPlayed/gamesWon/moves at their already-zero defaults
     */
    public static void loadStats() {
        statsFile = Path.of(GLib.getUserDataDir(), "gtklondike", "gamestats.xml");
        if(! Files.exists(statsFile)) {
            return;
        }

        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(statsFile.toFile());

            Element stats = (Element) document.getElementsByTagName("stats").item(0);
            moves = Integer.parseInt(stats.getElementsByTagName("moves").item(0).getTextContent());
            gamesPlayed = Integer.parseInt(stats.getElementsByTagName("gamesPlayed").item(0).getTextContent());
            gamesWon = Integer.parseInt(stats.getElementsByTagName("gamesWon").item(0).getTextContent());
        }
        catch(IOException | javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException exception) {
            throw new IllegalStateException("Could not read gamestats.xml.", exception);
        }
    }


    /**
     * persist the current stats to statsFile, creating its parent directory the first time this
     * runs. Called once, from GTKlondike.shutdown() - not after every move - since losing the
     * last few moves' worth of stats to a crash is an acceptable tradeoff for not touching disk
     * constantly.
     */
    public static void saveStats() {
        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.newDocument();

            Element stats = document.createElement("stats");
            document.appendChild(stats);
            appendChildWithText(document, stats, "moves", moves);
            appendChildWithText(document, stats, "gamesPlayed", gamesPlayed);
            appendChildWithText(document, stats, "gamesWon", gamesWon);

            Files.createDirectories(statsFile.getParent());
            Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(statsFile.toFile()));
        }
        catch(IOException | javax.xml.parsers.ParserConfigurationException | TransformerException exception) {
            throw new IllegalStateException("Could not write gamestats.xml.", exception);
        }
    }


    /**
     * append a new child element holding a single integer text value
     * @param document owning document, needed to create the element
     * @param parent element to append to
     * @param tagName child element name
     * @param value child element's text content
     */
    private static void appendChildWithText(Document document, Element parent, String tagName, int value) {
        Element child = document.createElement(tagName);
        child.setTextContent(String.valueOf(value));
        parent.appendChild(child);
    }


    /**
     * total moves++
     */
    public static void addMove() {
        moves++;
    }


    /**
     * return total moves played
     * @return moves played
     */
    public static int getMoves() {
        return moves;
    }


    /**
     * total games played++
     */
    public static void addGamePlayed() {
        gamesPlayed++;
    }


    /**
     * return total games played
     * @return games played
     */
    public static int getGamesPlayed() {
        return gamesPlayed;
    }


    /**
     * total games won++
     */
    public static void addGameWon() {
        gamesWon++;
    }


    /**
     * return total games won
     * @return games won
     */
    public static int getGamesWon() {
        return gamesWon;
    }
}
