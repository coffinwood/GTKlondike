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
import java.io.InputStream;
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
 * <p>
 * Each draw amount actually has two tiers, transparently merged: a read-only "bundled" set shipped
 * on the classpath (see BUNDLED_RESOURCE_PATH) - a "starter pack" so a fresh install can offer this
 * feature immediately instead of waiting for the player to grind out 50 wins themselves - and a
 * read/write "user" set persisted to this player's own solveddeals-draw*.xml. Only the user tier is
 * ever written to; the bundled tier is baked into the jar at build time.
 */
public class SolvedDeals {
    // how many solved deals a draw amount's library (bundled + user, combined) needs before "Deal
    // a Solvable Game" unlocks for it
    public static final int MIN_LIBRARY_SIZE_TO_OFFER = 50;
    // oldest user entries are evicted past this, purely to keep parse time bounded over years of
    // play - at ~150-180 bytes/entry this is nowhere near a real disk-space concern
    private static final int MAX_ENTRIES_PER_FILE = 1000;
    // classpath directory holding the shipped starter pack, using the exact same filenames (and
    // XML format, see loadDocument()) as the user's own solveddeals-draw*.xml - so shipping a
    // starter pack is just a matter of copying a played-in player's own files in here at build time
    private static final String BUNDLED_RESOURCE_DIR = "/solveddeals/";

    private static List<List<Card>> userDraw1Deals = new ArrayList<>();
    private static List<List<Card>> userDraw3Deals = new ArrayList<>();
    private static List<List<Card>> bundledDraw1Deals = new ArrayList<>();
    private static List<List<Card>> bundledDraw3Deals = new ArrayList<>();
    private static Path draw1File, draw3File;


    /**
     * load both draw-amount libraries: the user's writable solveddeals-draw*.xml files (or, the
     * first time either has been played to completion, an empty list), plus whatever starter-pack
     * deals are bundled on the classpath for each
     */
    public static void load() {
        Path dataDir = Path.of(GLib.getUserDataDir(), "gtklondike");
        draw1File = dataDir.resolve("solveddeals-draw1.xml");
        draw3File = dataDir.resolve("solveddeals-draw3.xml");
        userDraw1Deals = loadUserFile(draw1File);
        userDraw3Deals = loadUserFile(draw3File);
        bundledDraw1Deals = loadBundledResource("solveddeals-draw1.xml");
        bundledDraw3Deals = loadBundledResource("solveddeals-draw3.xml");
    }


    /**
     * parse one user library file into its list of deck orders
     * @param file library file to read
     * @return the file's deck orders, or an empty list if the file doesn't exist yet
     */
    private static List<List<Card>> loadUserFile(Path file) {
        if(! Files.exists(file)) {
            return new ArrayList<>();
        }
        try(InputStream inputStream = Files.newInputStream(file)) {
            return loadDocument(inputStream);
        }
        catch(IOException exception) {
            throw new IllegalStateException("Could not read " + file.getFileName() + ".", exception);
        }
    }


    /**
     * parse one bundled starter-pack resource into its list of deck orders
     * @param resourceName file name, resolved under BUNDLED_RESOURCE_DIR on the classpath
     * @return the resource's deck orders, or an empty list if no starter pack was shipped for it
     */
    private static List<List<Card>> loadBundledResource(String resourceName) {
        try(InputStream inputStream = SolvedDeals.class.getResourceAsStream(BUNDLED_RESOURCE_DIR + resourceName)) {
            if(inputStream == null) {
                return new ArrayList<>();
            }
            return loadDocument(inputStream);
        }
        catch(IOException exception) {
            throw new IllegalStateException("Could not read bundled " + resourceName + ".", exception);
        }
    }


    /**
     * parse a solvedDeals XML document (from either a user file or a bundled resource) into its
     * list of deck orders
     * @param inputStream document contents
     * @return the document's deck orders
     */
    private static List<List<Card>> loadDocument(InputStream inputStream) throws IOException {
        List<List<Card>> deals = new ArrayList<>();
        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(inputStream);

            NodeList dealNodes = document.getElementsByTagName("deal");
            for(int dealIndex = 0; dealIndex < dealNodes.getLength(); dealIndex++) {
                deals.add(decodeDeck(dealNodes.item(dealIndex).getTextContent()));
            }
        }
        catch(javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException exception) {
            throw new IllegalStateException("Could not parse solved-deals document.", exception);
        }
        return deals;
    }


    /**
     * persist one user library file
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
     * is this exact deck order already present in either tier of a draw amount's library?
     * @param drawAmount draw amount (1 or 3)
     * @param deckOrder deck order to look for
     * @return TRUE if a matching deck is already in the bundled or user tier
     */
    private static boolean isAlreadyKnown(int drawAmount, List<Card> deckOrder) {
        for(List<Card> existing : userDeals(drawAmount)) {
            if(decksEqual(existing, deckOrder)) {
                return true;
            }
        }
        for(List<Card> existing : bundledDeals(drawAmount)) {
            if(decksEqual(existing, deckOrder)) {
                return true;
            }
        }
        return false;
    }


    /**
     * record a newly-won deck as solvable into the user tier, unless that exact deck order is
     * already known (in either tier) for the matching draw amount. Saves to disk immediately
     * (unlike Statistics' shutdown-only save) since a solved deal is a rare, small, valuable event
     * worth not risking to a crash before the next shutdown.
     * @param drawAmount the draw amount this deck was won under (1 or 3)
     * @param deckOrder the deck's original deal order
     */
    public static void recordSolvedDeal(int drawAmount, List<Card> deckOrder) {
        if(isAlreadyKnown(drawAmount, deckOrder)) {
            return;
        }

        List<List<Card>> userDeals = userDeals(drawAmount);
        userDeals.add(cloneDeck(deckOrder));
        while(userDeals.size() > MAX_ENTRIES_PER_FILE) {
            userDeals.removeFirst();
        }
        saveFile(drawAmount == 3 ? draw3File : draw1File, userDeals);
    }


    /**
     * number of solved deals currently in a draw amount's library (bundled + user, combined)
     * @param drawAmount draw amount (1 or 3)
     * @return library size
     */
    public static int getLibrarySize(int drawAmount) {
        return userDeals(drawAmount).size() + bundledDeals(drawAmount).size();
    }


    /**
     * has a draw amount's library grown large enough to offer "Deal a Solvable Game" for it?
     * @param drawAmount draw amount (1 or 3)
     * @return TRUE if the combined library has at least MIN_LIBRARY_SIZE_TO_OFFER entries
     */
    public static boolean hasEnoughSolvedDeals(int drawAmount) {
        return getLibrarySize(drawAmount) >= MIN_LIBRARY_SIZE_TO_OFFER;
    }


    /**
     * pick a uniformly random solved deal from a draw amount's combined (bundled + user) library
     * @param drawAmount draw amount (1 or 3)
     * @return a fresh clone of a randomly chosen deck order, or null if that library is empty
     */
    public static List<Card> pickRandomDeal(int drawAmount) {
        List<List<Card>> userDeals = userDeals(drawAmount);
        List<List<Card>> bundledDeals = bundledDeals(drawAmount);
        int totalSize = userDeals.size() + bundledDeals.size();
        if(totalSize == 0) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(totalSize);
        List<Card> chosen = index < bundledDeals.size()
                ? bundledDeals.get(index)
                : userDeals.get(index - bundledDeals.size());
        return cloneDeck(chosen);
    }


    /**
     * the in-memory user-tier library list for a draw amount
     * @param drawAmount draw amount (1 or 3)
     * @return userDraw3Deals if drawAmount is 3, userDraw1Deals otherwise
     */
    private static List<List<Card>> userDeals(int drawAmount) {
        return drawAmount == 3 ? userDraw3Deals : userDraw1Deals;
    }


    /**
     * the in-memory bundled-tier library list for a draw amount
     * @param drawAmount draw amount (1 or 3)
     * @return bundledDraw3Deals if drawAmount is 3, bundledDraw1Deals otherwise
     */
    private static List<List<Card>> bundledDeals(int drawAmount) {
        return drawAmount == 3 ? bundledDraw3Deals : bundledDraw1Deals;
    }
}