package org.coffinwood.gtklondike;

import io.github.jwharm.javagi.base.GErrorException;
import org.gnome.gio.Settings;
import org.gnome.gio.SettingsSchema;
import org.gnome.gio.SettingsSchemaSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


/**
 * thin wrapper around the app's GSettings schema (org.coffinwood.gtklondike),
 * compiled at build time by the "compileGSettingsSchemas" gradle task into
 * build/resources/main/schemas/gschemas.compiled
 */
public class GamePreferences {
    private final Settings settings;


    /**
     * constructor; loads and looks up the compiled schema from the classpath
     */
    public GamePreferences() {
        settings = loadSettings();
    }


    /**
     * extract the compiled schema to a real temp directory and load it from there.
     * GLib's SettingsSchemaSource.fromDirectory() is a native call that needs an actual
     * filesystem directory, so a classpath resource URL (which may point inside a packed/shadow
     * jar, with no corresponding real path) can't be used directly - copying the bytes out to a
     * temp file works the same way whether running exploded or from a jar
     * @return Settings object bound to the org.coffinwood.gtklondike schema
     */
    private static Settings loadSettings() {
        try {
            Path schemaDir = extractCompiledSchema();
            SettingsSchemaSource source = SettingsSchemaSource.fromDirectory(
                    schemaDir.toString(), SettingsSchemaSource.getDefault(), true);
            SettingsSchema schema = source.lookup("org.coffinwood.gtklondike", false);
            if(schema == null) {
                throw new IllegalStateException("Schema org.coffinwood.gtklondike not found in " + schemaDir);
            }
            return Settings.full(schema, null, null);
        }
        catch(IOException | GErrorException exception) {
            throw new IllegalStateException("Could not load GSettings schema.", exception);
        }
    }


    /**
     * copy the "/schemas/gschemas.compiled" classpath resource into a fresh temp directory
     * @return the temp directory containing the copied schema file
     */
    private static Path extractCompiledSchema() throws IOException {
        try(InputStream inputStream = GamePreferences.class.getResourceAsStream("/schemas/gschemas.compiled")) {
            if(inputStream == null) {
                throw new IllegalStateException("Compiled GSettings schema not found on classpath.");
            }
            Path schemaDir = Files.createTempDirectory("gtklondike-schemas");
            Path compiledSchemaFile = schemaDir.resolve("gschemas.compiled");
            Files.copy(inputStream, compiledSchemaFile, StandardCopyOption.REPLACE_EXISTING);
            // reverse-order shutdown hooks mean the file is deleted before the (then-empty) dir
            schemaDir.toFile().deleteOnExit();
            compiledSchemaFile.toFile().deleteOnExit();
            return schemaDir;
        }
    }


    /**
     * the underlying Settings object, e.g. for binding a widget property directly to a key
     * @return Settings object
     */
    public Settings getSettings() {
        return settings;
    }


    /**
     * number of cards drawn from the stock at a time (1 or 3)
     * @return draw amount
     */
    public int getDrawAmount() {
        return settings.getInt("draw-amount");
    }


    /**
     * set the number of cards drawn from the stock at a time
     * @param drawAmount 1 or 3
     */
    public void setDrawAmount(int drawAmount) {
        settings.setInt("draw-amount", drawAmount);
    }


    /**
     * card size multiplier
     * @return card scale
     */
    public double getCardScale() {
        return settings.getDouble("card-scale");
    }



    /**
     * name of the selected card back texture (one of CardImages.BACK_TEXTURE_NAMES)
     * @return card back texture name
     */
    public String getCardBack() {
        return settings.getString("card-back");
    }


    /**
     * set the selected card back texture
     * @param cardBack one of CardImages.BACK_TEXTURE_NAMES
     */
    public void setCardBack(String cardBack) {
        settings.setString("card-back", cardBack);
    }


    /**
     * name of the selected background CSS class (a "bg_" class discovered from the stylesheet).
     * Deliberately not constrained by <choices> in the schema (unlike card-back): the whole point
     * is that adding a new .bg_xxx rule to the stylesheet makes it available with no Java or
     * schema changes, so the persisted value is just a free-form string.
     * @return background CSS class name
     */
    public String getBackground() {
        return settings.getString("background");
    }


    /**
     * set the selected background CSS class
     * @param background a "bg_"-prefixed CSS class name
     */
    public void setBackground(String background) {
        settings.setString("background", background);
    }


    /**
     * is the elapsed-time timer shown in the header bar?
     * @return TRUE if the timer should be visible
     */
    public boolean isShowTimer() {
        return settings.getBoolean("show-timer");
    }


    /**
     * set whether the elapsed-time timer is shown in the header bar
     * @param showTimer TRUE to show the timer
     */
    public void setShowTimer(boolean showTimer) {
        settings.setBoolean("show-timer", showTimer);
    }
}
