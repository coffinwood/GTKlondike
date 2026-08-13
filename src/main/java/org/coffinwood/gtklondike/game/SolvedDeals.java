package org.coffinwood.gtklondike.game;

import org.gnome.glib.GLib;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


/**
 * a library of decks proven solvable because a player actually won them, split into one library
 * per draw amount (a deck's solvability depends on how many cards are drawn from the stock at a
 * time). Lets the UI offer a "Deal a Solvable Game" option once a draw amount's library has grown
 * large enough that reusing one still feels like an ordinary random deal.
 */
public class SolvedDeals {
    // how many solved deals a draw amount's library needs before "Deal a Solvable Game" unlocks
    // for it
    // TODO temporarily lowered from 50 for manual testing - restore to 50 before release
    public static final int MIN_LIBRARY_SIZE_TO_OFFER = 1;
    // oldest entries are evicted past this, purely to keep parse time bounded over years of play -
    // at ~150-180 bytes/entry this is nowhere near a real disk-space concern
    private static final int MAX_ENTRIES_PER_FILE = 1000;

    private static List<List<Card>> draw1Deals = new ArrayList<>();
    private static List<List<Card>> draw3Deals = new ArrayList<>();
    private static Path draw1File, draw3File;


    /**
     * load both draw-amount libraries from the user's writable solveddeals-draw*.xml files - or,
     * the first time either has been played to completion, leave that list empty
     */
    public static void load() {
        Path dataDir = Path.of(GLib.getUserDataDir(), "gtklondike");
        draw1File = dataDir.resolve("solveddeals-draw1.xml");
        draw3File = dataDir.resolve("solveddeals-draw3.xml");
        draw1Deals = loadFile(draw1File);
        draw3Deals = loadFile(draw3File);
    }


    /**
     * parse one library file into its list of deck orders
     * @param file library file to read
     * @return the file's deck orders, or an empty list if the file doesn't exist yet
     */
    private static List<List<Card>> loadFile(Path file) {
        List<List<Card>> deals = new ArrayList<>();
        if(! Files.exists(file)) {
            return deals;
        }

        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(file.toFile());

            NodeList dealNodes = document.getElementsByTagName("deal");
            for(int dealIndex = 0; dealIndex < dealNodes.getLength(); dealIndex++) {
                deals.add(decodeDeck(dealNodes.item(dealIndex).getTextContent()));
            }
        }
        catch(IOException | javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException exception) {
            throw new IllegalStateException("Could not read " + file.getFileName() + ".", exception);
        }
        return deals;
    }


    /**
     * persist one library file
     * @param file library file to write
     * @param deals deck orders to write, in order
     */
    private static void saveFile(Path file, List<List<Card>> deals) {
        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.newDocument();

            Element root = document.createElement("solvedDeals");
            document.appendChild(root);
            for(List<Card> deal : deals) {
                Element dealElement = document.createElement("deal");
                dealElement.setTextContent(encodeDeck(deal));
                root.appendChild(dealElement);
            }

            Files.createDirectories(file.getParent());
            Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(file.toFile()));
        }
        catch(IOException | javax.xml.parsers.ParserConfigurationException | TransformerException exception) {
            throw new IllegalStateException("Could not write " + file.getFileName() + ".", exception);
        }
    }


    /**
     * encode a deck order as "isBlack-suit-rank" triples, comma-separated - directly serializes
     * the three fields Card's constructor takes, so no separate suit/rank-to-letter mapping needs
     * to be invented
     * @param deck deck order to encode
     * @return encoded deck
     */
    private static String encodeDeck(List<Card> deck) {
        StringBuilder builder = new StringBuilder();
        for(Card card : deck) {
            if(! builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(card.isBlack() ? '1' : '0').append('-')
                    .append(card.getSuit()).append('-')
                    .append(card.getRank());
        }
        return builder.toString();
    }


    /**
     * decode a deck order previously written by encodeDeck()
     * @param encoded encoded deck
     * @return decoded deck order
     */
    private static List<Card> decodeDeck(String encoded) {
        List<Card> deck = new ArrayList<>();
        for(String cardText : encoded.split(",")) {
            String[] parts = cardText.split("-");
            deck.add(new Card("1".equals(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
        return deck;
    }


    /**
     * deep-clone a deck (fresh Card instances), same reasoning as Game's own cloneDeck(): later
     * mutations (e.g. isFaceUp during play) must never corrupt a stored/archived order
     * @param deck deck to clone
     * @return cloned deck
     */
    private static List<Card> cloneDeck(List<Card> deck) {
        List<Card> clone = new ArrayList<>(deck.size());
        for(Card card : deck) {
            clone.add(new Card(card.isBlack(), card.getSuit(), card.getRank()));
        }
        return clone;
    }


    /**
     * are two deck orders the same 52 cards in the same order?
     * @param first first deck
     * @param second second deck
     * @return TRUE if every card matches, in order
     */
    private static boolean decksEqual(List<Card> first, List<Card> second) {
        if(first.size() != second.size()) {
            return false;
        }
        for(int cardIndex = 0; cardIndex < first.size(); cardIndex++) {
            Card a = first.get(cardIndex), b = second.get(cardIndex);
            if(a.getSuit() != b.getSuit() || a.getRank() != b.getRank()) {
                return false;
            }
        }
        return true;
    }


    /**
     * record a newly-won deck as solvable, unless that exact deck order is already in the
     * matching draw amount's library. Saves to disk immediately (unlike Statistics' shutdown-only
     * save) since a solved deal is a rare, small, valuable event worth not risking to a crash
     * before the next shutdown.
     * @param drawAmount the draw amount this deck was won under (1 or 3)
     * @param deckOrder the deck's original deal order
     */
    public static void recordSolvedDeal(int drawAmount, List<Card> deckOrder) {
        List<List<Card>> deals = deals(drawAmount);
        for(List<Card> existing : deals) {
            if(decksEqual(existing, deckOrder)) {
                return;
            }
        }

        deals.add(cloneDeck(deckOrder));
        while(deals.size() > MAX_ENTRIES_PER_FILE) {
            deals.remove(0);
        }
        saveFile(drawAmount == 3 ? draw3File : draw1File, deals);
    }


    /**
     * number of solved deals currently in a draw amount's library
     * @param drawAmount draw amount (1 or 3)
     * @return library size
     */
    public static int getLibrarySize(int drawAmount) {
        return deals(drawAmount).size();
    }


    /**
     * has a draw amount's library grown large enough to offer "Deal a Solvable Game" for it?
     * @param drawAmount draw amount (1 or 3)
     * @return TRUE if the library has at least MIN_LIBRARY_SIZE_TO_OFFER entries
     */
    public static boolean hasEnoughSolvedDeals(int drawAmount) {
        return getLibrarySize(drawAmount) >= MIN_LIBRARY_SIZE_TO_OFFER;
    }


    /**
     * pick a uniformly random solved deal from a draw amount's library
     * @param drawAmount draw amount (1 or 3)
     * @return a fresh clone of a randomly chosen deck order, or null if that library is empty
     */
    public static List<Card> pickRandomDeal(int drawAmount) {
        List<List<Card>> deals = deals(drawAmount);
        if(deals.isEmpty()) {
            return null;
        }
        return cloneDeck(deals.get(ThreadLocalRandom.current().nextInt(deals.size())));
    }


    /**
     * the in-memory library list for a draw amount
     * @param drawAmount draw amount (1 or 3)
     * @return draw3Deals if drawAmount is 3, draw1Deals otherwise
     */
    private static List<List<Card>> deals(int drawAmount) {
        return drawAmount == 3 ? draw3Deals : draw1Deals;
    }
}
