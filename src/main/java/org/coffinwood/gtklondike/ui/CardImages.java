package org.coffinwood.gtklondike.ui;

import io.github.jwharm.javagi.base.GErrorException;
import org.coffinwood.gtklondike.game.Card;
import org.gnome.gdk.Texture;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * load face and back images for all playing cards into an array
 */
public class CardImages {
    // names (without the back_/.png parts) of the available card back textures in resources/images
    public static final String[] BACK_TEXTURE_NAMES = { "back_blue", "back_grey" };
    private static final String DEFAULT_BACK_TEXTURE_NAME = BACK_TEXTURE_NAMES[0];

    private static final Texture[][] faceTextures = new Texture[4][13];
    private static final Map<String, Texture> backTextures = new LinkedHashMap<>();
    private static String selectedBackTextureName = DEFAULT_BACK_TEXTURE_NAME;
    private static boolean areAssetsLoaded = false;


    /**
     * load graphics into objects; safe to call more than once
     */
    public static void loadAssets() {
        try {
            for(int suitIndex = 0; suitIndex < 4; suitIndex++) {
                for(int rankIndex = 0; rankIndex < 13; rankIndex++) {
                    faceTextures[suitIndex][rankIndex] = loadTexture("/images/" + suitIndex + "_" + rankIndex + ".png");
                }
            }
            for(String backTextureName : BACK_TEXTURE_NAMES) {
                backTextures.put(backTextureName, loadTexture("/images/" + backTextureName + ".png"));
            }
        }
        catch(IOException | GErrorException exception) {
            throw new IllegalStateException("Error while loading assets.", exception);
        }
        areAssetsLoaded = true;
    }


    /**
     * load a Texture from image bytes read off the classpath; unlike Texture.fromFilename(), this
     * doesn't need a real filesystem path, so it works from inside a packed (shadow) jar too
     * @param fileName classpath resource path
     * @return Texture object with loaded image
     * @throws GErrorException if the image bytes can't be decoded
     */
    private static Texture loadTexture(String fileName) throws IOException, GErrorException {
        try(InputStream inputStream = CardImages.class.getResourceAsStream(fileName)) {
            if(inputStream == null) {
                throw new IllegalStateException("Resource " + fileName + " not found.");
            }
            return Texture.fromBytes(inputStream.readAllBytes());
        }
    }


    /**
     * get the Texture object for the given suit
     * @param card card
     * @return Texture object
     */
    public static Texture getFaceTexture(Card card) {
        if(! areAssetsLoaded) {
            loadAssets();
        }
        return faceTextures[card.getSuit()][card.getRank()];
    }


    /**
     * get the currently selected back texture
     * @return back texture
     */
    public static Texture getBackTexture() {
        return getBackTexture(selectedBackTextureName);
    }


    /**
     * get a specific back texture by name, e.g. for a preview thumbnail
     * @param backTextureName one of BACK_TEXTURE_NAMES
     * @return back texture
     */
    public static Texture getBackTexture(String backTextureName) {
        if(! areAssetsLoaded) {
            loadAssets();
        }
        return backTextures.get(backTextureName);
    }


    /**
     * select which back texture getBackTexture() returns; does nothing if the name is unknown
     * @param backTextureName one of BACK_TEXTURE_NAMES
     */
    public static void setBackTexture(String backTextureName) {
        if(! areAssetsLoaded) {
            loadAssets();
        }
        if(backTextures.containsKey(backTextureName)) {
            selectedBackTextureName = backTextureName;
        }
    }
}
