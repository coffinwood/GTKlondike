package org.coffinwood.gtklondike;

import io.github.jwharm.javagi.gobject.types.Types;
import org.coffinwood.gtklondike.game.*;
import org.coffinwood.gtklondike.ui.*;
import org.coffinwood.gtklondike.util.*;
import org.gnome.gdk.Display;
import org.gnome.gdk.DragAction;
import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.SettingsBindFlags;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GLib;
import org.gnome.glib.Type;
import org.gnome.gtk.*;
import org.gnome.pango.WrapMode;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Supplier;


/**
 * GTKlondike is a Java implementation of the simple Klondike Solitaire variant
 * with bindings to GTK to run as "native" as possible on Gnome although the GTK language is C.
 * TODO bugs:
 * TODO - it seems as if the game occasionally "swallows" consecutive double clicks. hard to prove - and hereby - fix
 *          - maybe happens when a card gets turned face-up after a double click. have to watch this behaviour.
 */
class GTKlondike extends Application {
    public static String APP_TITLE = "GTKlondike";
    public static String APP_VERSION = "1.3";
    // base (unscaled) card pixel width; PileLayoutManager derives height as width * 1.4
    private static final int BASE_CARD_WIDTH = 150;
    private static final int UI_SPACING_ROW_X = 12, UI_SPACING_ROW_Y = 12;
    // inner padding around the whole board, so piles don't sit flush against the window edges
    private static final int BOARD_PADDING = 20;
    // pause between each auto-completed card so the player can watch it happen - short enough to
    // still read as "fast-forwarding" rather than the game just sitting there card by card
    private static final int AUTOMOVE_DELAY = 80;
    // how much of the board's width the victory banner spans
    private static final double VICTORY_BANNER_WIDTH_FRACTION = 0.8;
    // height/width of congratulations.svg's own viewBox (3100 x 880.00002), so the banner's
    // size request keeps its aspect ratio instead of leaving "background-size: contain" to
    // letterbox an arbitrarily-shaped box
    private static final double VICTORY_BANNER_ASPECT_RATIO = 880.0 / 3100.0;

    private Game game;
    // the board's persistent/pooled pile and card widgets, built once in buildBoardOnce() and
    // reused for the app's whole lifetime - see BoardWidgets
    private BoardWidgets boardWidgets;
    private GamePreferences preferences;
    private ApplicationWindow window;
    // the window's actual child; holds the board as its main child and the victory banner (see
    // celebrateVictory()/clearVictoryBanner()) as a floating overlay on top of it, so the banner
    // never has to touch the window's own "bg_" wallpaper background-image
    private Overlay boardOverlay;
    // the board Grid, built once in buildBoardOnce() and kept as a field so celebrateVictory()
    // can read its allocated width to size the victory banner
    private Grid gameBoard;
    // frosted-glass "you won" banner, shown/hidden via boardOverlay.addOverlay()/removeOverlay()
    private Box victoryBanner;
    // running-stats line ("So far, you've played...") stacked under victoryBanner - a separate
    // sibling, not a child of it, since victoryBanner's own background image already fills its
    // whole (aspect-ratio-locked) box via "background-size: contain", leaving no room inside it
    // for a label without overlapping the image
    private Label victoryStatsLabel;
    // wraps victoryBanner + victoryStatsLabel as the single widget actually added/removed via
    // boardOverlay.addOverlay()/removeOverlay() - see celebrateVictory()/clearVictoryBanner()
    private Box victoryOverlay;
    // frosted-glass "paused" cover, shown/hidden via boardOverlay.addOverlay()/removeOverlay() -
    // see pauseGame()/resumeGame(). Unlike victoryBanner it fills the whole board (no halign/valign
    // set), so the position underneath is fully hidden rather than just centrally obscured
    private Box pauseOverlay;
    // enabled/disabled from Game.onGameStateChanged to track canUndo()/canAutoComplete()
    private SimpleAction undoAction;
    private SimpleAction autoCompleteAction;
    // disabled/re-enabled around victory (see celebrateVictory()/clearVictoryBanner()); everything
    // else about "restart" is otherwise always available, so it isn't synced from Game state
    private SimpleAction restartAction;
    // disabled/re-enabled around a pause (see pauseGame()/resumeGame()) - the Preferences dialogue
    // has a "Show timer" toggle, which would otherwise let a paused player quietly hide the timer
    // (and read as being able to fiddle with settings on a "frozen" board generally)
    private SimpleAction preferencesAction;
    // the "p"/Pause-key shortcut for the header bar's play/pause button (see toggleTimer());
    // disabled/re-enabled around victory alongside gameTimer's own toggle button, since
    // pauseGame()'s own game.isVictory() guard already makes the accelerator a no-op then anyway -
    // disabling the action too is just for a consistent "nothing left to pause" UI state
    private SimpleAction pauseAction;
    // glowed on victory to invite the next click, via the same "button_hint_glow" CSS class the
    // auto-complete button already uses
    private Button newGameButton;
    // header-bar elapsed-time display, packed next to the auto-complete button (see buildHeaderBar())
    private GameTimer gameTimer;
    // TRUE while the player has explicitly paused (pauseOverlay showing, board input blocked) -
    // distinct from the timer's own running/paused state, which is also stopped once the game is
    // won (see celebrateVictory()), where none of this pause machinery applies
    private boolean isPaused;
    // names (with the leading "bg_", e.g. "bg_trees") of every "bg_"-prefixed CSS class found in
    // the stylesheet, discovered once at startup so the Preferences background picker stays in
    // sync with the stylesheet without needing a matching Java-side list to maintain
    private List<String> backgroundClassNames;


    /**
     * constructor
     */
    public GTKlondike() {
        setApplicationId("org.coffinwood.gtklondike.GTKlondike");
        setFlags(ApplicationFlags.DEFAULT_FLAGS);
    }


    /**
     * main function
     * @param args arguments
     */
    void main(String[] args) {
        Application app = new GTKlondike();
        app.run(args);
        app.unref();
    }


    /**
     * activate app window
     */
    @Override
    public void activate() {
        // call the loading of the cards' textures once
        CardImages.loadAssets();
        preferences = new GamePreferences();
        CardImages.setBackTexture(preferences.getCardBack());
        setupActions();

        // load game stats
        Statistics.loadStats();

        // load CSS stylesheet
        CssProvider cssProvider = new CssProvider();
        String cssText = Utilities.loadResourceText("/style/gtklondike.css");
        if(cssText.isEmpty()) {
            throw new IllegalStateException("CSS stylesheet could not be loaded.");
        }
        cssProvider.loadFromString(cssText);
        Gtk.styleContextAddProviderForDisplay(Display.getDefault(), cssProvider, 800);
        backgroundClassNames = Utilities.discoverCssBackgroundClasses(cssText);

        // prepare the application window
        window = new ApplicationWindow(this);
        applyBackground(preferences.getBackground());
        window.setTitle(APP_TITLE);
        window.setTitlebar(buildHeaderBar());
        gameTimer.setVisible(preferences.isShowTimer());
        // <= 0 means "use the natural size request" (derived from PileLayoutManager.measure()
        // up through the Grid), instead of a hardcoded size unrelated to actual content/scale
        window.setDefaultSize(-1, -1);

        // Overlay's own natural size tracks only its main child (the board), not overlay
        // children, so wrapping the board here doesn't change the sizing behaviour above
        boardOverlay = Overlay.builder().build();
        window.setChild(boardOverlay);

        victoryBanner = Box.builder().setHalign(Align.CENTER).setValign(Align.CENTER).build();
        // reuses .card_pile_base's frosted-glass blur/border/shadow; .victory_banner only adds
        // the background-image (see gtklondike.css)
        victoryBanner.addCssClass("card_pile_base");
        victoryBanner.addCssClass("victory_banner");

        victoryStatsLabel = Label.builder()
                .setJustify(Justification.CENTER)
                .setHalign(Align.CENTER)
                .setWrap(true)
                .build();
        victoryStatsLabel.addCssClass("victory_stats_label");

        victoryOverlay = Box.builder()
                .setOrientation(Orientation.VERTICAL)
                .setSpacing(12)
                .setHalign(Align.CENTER)
                .setValign(Align.CENTER)
                .build();
        victoryOverlay.append(victoryBanner);
        victoryOverlay.append(victoryStatsLabel);

        pauseOverlay = buildPauseOverlay();

        // the board's pile/card widgets are built once and reused for every New Game/Restart/
        // Undo from here on - see BoardWidgets and buildBoardOnce()
        boardWidgets = new BoardWidgets();
        buildBoardOnce();

        // start a new game and show the window
        game = new Game(preferences.getDrawAmount(), boardWidgets);
        onGameReplaced();
        gameTimer.reset();
        window.present();
    }


    /**
     * persist game stats once, when the app is actually closing - see Statistics.saveStats() for
     * why this doesn't happen after every move instead
     * NOTE: this might lose players "game progress" should the app crash, but prevents constant writing to the drive.
     */
    @Override
    public void shutdown() {
        Statistics.saveStats();
        // GApplication requires overridden vfuncs to chain up to the parent implementation -
        // without this, GLib logs a "failed to chain up on ::shutdown" critical and its own
        // teardown (unregistering, releasing the hold that's keeping the main loop alive, etc.)
        // never runs
        super.shutdown();
    }


    /**
     * build the Grid and attach the board's 13 persistent pile widgets into it at their
     * (permanently fixed) positions, and wire up their drop targets - all exactly once, since
     * boardWidgets' widgets are reused rather than rebuilt for every New Game/Restart/Undo.
     * See onGameReplaced() for the part of the old rebuildBoard() that *does* still need to run
     * after each of those.
     */
    private void buildBoardOnce() {
        gameBoard = Grid.builder()
                .setHalign(Align.CENTER)
                .setValign(Align.CENTER)
                .setRowSpacing(UI_SPACING_ROW_X)
                .setColumnSpacing(UI_SPACING_ROW_Y)
                .setRowHomogeneous(false)
                .setColumnHomogeneous(true)
                .setMarginTop(BOARD_PADDING)
                .setMarginBottom(BOARD_PADDING)
                .setMarginStart(BOARD_PADDING)
                .setMarginEnd(BOARD_PADDING)
                .build();

        // add stock and waste card piles
        gameBoard.attach(boardWidgets.getStockWidget(), 0, 0, 1, 1);
        // spans the otherwise-empty column between the waste pile and the foundations (column 2)
        // so a draw-3 fan has room to spread out instead of stacking flush; harmless in draw-1
        // mode, where the fan never actually spreads (see PileLayoutManager.setFanCount())
        gameBoard.attach(boardWidgets.getWasteWidget(), 1, 0, 2, 1);

        // in the grid layout (7 columns, 2 rows), stock goe into 0, waste spans over 1 and 2, foundations start at 3
        int pileIndex = 3;
        // add foundation card piles
        for(int suit = 0; suit < 4; suit++) {
            int capturedSuit = suit;
            attachDropTarget(boardWidgets.getFoundationWidget(suit), () -> game.getFoundationPiles().get(capturedSuit));
            gameBoard.attach(boardWidgets.getFoundationWidget(suit), pileIndex, 0, 1, 1);
            pileIndex++;
        }

        // add tableau card piles in a new row
        pileIndex = 0;
        for(int lane = 0; lane < 7; lane++) {
            int capturedLane = lane;
            attachDropTarget(boardWidgets.getTableauWidget(lane), () -> game.getTableauPiles().get(capturedLane));
            gameBoard.attach(boardWidgets.getTableauWidget(lane), pileIndex, 1, 1, 1);
            pileIndex++;
        }

        boardOverlay.setChild(gameBoard);
    }


    /**
     * re-wire everything that depends on the *current* `game` instance - called after New Game/
     * Restart/Undo replace it (or repopulate its piles). Unlike the old rebuildBoard(), this
     * never touches the widget tree: boardWidgets' pile/card widgets are permanent, and
     * Game.deal()/restoreSnapshot() already repopulated them via BoardWidgets' pool.
     */
    private void onGameReplaced() {
        // drop any selection referencing the piles this replaces
        SelectionManager.init(game);
        game.onVictory = this::celebrateVictory;
        game.onGameStateChanged = this::handleGameStateChanged;
        // also covers the case where New Game/Restart/Undo cleared or repopulated the undo stack
        // (and thus canAutoComplete()) of a game that already existed
        updateActionStates();
        applyCardScale(preferences.getCardScale());
        // celebrateVictory() hides the board without unparenting it (see there) - undo that here
        // too, since a New Game/Restart from the victory screen goes through this same method
        gameBoard.setVisible(true);
    }


    /**
     * Game.onGameStateChanged callback: fires after every draw/move/undo. Syncs the header-bar
     * action states and lazily starts the timer.
     */
    private void handleGameStateChanged() {
        updateActionStates();
        // lazy-start: the clock doesn't run until the player actually makes the first move/draw,
        if(! isPaused && ! game.isVictory()) {
            gameTimer.start();
        }
    }


    /**
     * sync the Undo/Auto-Complete actions' enabled state with Game.canUndo()/canAutoComplete();
     * GTK disables/enables the header-bar buttons bound to these actions automatically since
     * they're tied via setActionName(). A no-op while the game is won: Game.tryMove() fires
     * checkForVictory() (-> celebrateVictory(), which disables these same actions) just before
     * notifyGameStateChanged() (-> handleGameStateChanged() -> this method) for the winning move
     * itself, so without this guard the "current" canUndo()/canAutoComplete() values here would
     * immediately re-enable both buttons right after celebrateVictory() disabled them
     */
    private void updateActionStates() {
        if(game.isVictory()) {
            return;
        }
        undoAction.setEnabled(game.canUndo());
        autoCompleteAction.setEnabled(game.canAutoComplete());
    }


    /**
     * play one auto-complete card, then - as long as there was one to play - schedule the next
     * one after AUTO_COMPLETE_STEP_DELAY_MS instead of looping immediately, so each move actually
     * gets painted and the player can watch the game finish itself rather than seeing it jump
     * straight to "won". Runs through GLib's main-loop timer, not a blocking sleep, so the UI
     * (and any other input) stays responsive between steps.
     */
    private void runAutoCompleteStep() {
        // same drag-safety reasoning as undo: removeRunWidgets() unparents widgets, which must
        // not happen while GTK's native drag machinery still owns one of them. Rather than
        // abandoning the run, just wait the drag out and retry.
        if(CardWidget.currentDragPayload != null) {
            GLib.timeoutAddOnce(AUTOMOVE_DELAY, this::runAutoCompleteStep);
            return;
        }
        if(game.autoCompleteStep()) {
            GLib.timeoutAddOnce(AUTOMOVE_DELAY, this::runAutoCompleteStep);
        }
    }


    /**
     * app-level actions (new game, restart, preferences, about) and their accelerators
     */
    private void setupActions() {
        SimpleAction newGameAction = new SimpleAction("new-game", null);
        newGameAction.onActivate(parameter -> {
            game = new Game(preferences.getDrawAmount(), boardWidgets);
            onGameReplaced();
            clearVictoryBanner();
            gameTimer.reset();
        });
        addAction(newGameAction);
        setAccelsForAction("app.new-game", new String[] { "<Control>n" });

        restartAction = new SimpleAction("restart", null);
        restartAction.onActivate(parameter -> {
            game.restart();
            onGameReplaced();
            clearVictoryBanner();
            gameTimer.reset();
        });
        addAction(restartAction);
        setAccelsForAction("app.restart", new String[] { "<Control><Shift>r" });

        undoAction = new SimpleAction("undo", null);
        undoAction.onActivate(parameter -> {
            // Game.undo()/restoreSnapshot() reclaims and re-deals every persistent pile widget's
            // cards outright; doing that while GTK's native drag machinery still owns a CardWidget
            // from an in-progress drag risks the same 'real_box->magic == G_BOX_MAGIC' assertion
            // crash the drag code elsewhere works around, so undo is simply ignored until the drag
            // finishes
            if(CardWidget.currentDragPayload == null && game.undo()) {
                onGameReplaced();
            }
        });
        addAction(undoAction);
        setAccelsForAction("app.undo", new String[] { "<Control>z" });

        autoCompleteAction = new SimpleAction("auto-complete", null);
        autoCompleteAction.onActivate(parameter -> runAutoCompleteStep());
        addAction(autoCompleteAction);
        setAccelsForAction("app.auto-complete", new String[] { "<Control><Shift>a" });

        pauseAction = new SimpleAction("toggle-pause", null);
        pauseAction.onActivate(parameter -> toggleTimer());
        addAction(pauseAction);
        setAccelsForAction("app.toggle-pause", new String[] { "p", "Pause" });

        preferencesAction = new SimpleAction("preferences", null);
        preferencesAction.onActivate(parameter -> showPreferencesDialog());
        addAction(preferencesAction);

        SimpleAction aboutAction = new SimpleAction("about", null);
        aboutAction.onActivate(parameter -> showAboutDialog());
        addAction(aboutAction);

        SimpleAction helpAction = new SimpleAction("help", null);
        helpAction.onActivate(parameter -> showHelpDialog());
        addAction(helpAction);
    }


    /**
     * build the header bar with New Game / Restart / Undo / Auto-Complete / Preferences / About
     * buttons
     * @return header bar
     */
    private HeaderBar buildHeaderBar() {
        HeaderBar headerBar = HeaderBar.builder().build();

        newGameButton = Button.builder()
                .setIconName("document-new-symbolic")
                .setTooltipText("New Game")
                .setActionName("app.new-game")
                .build();
        Button restartButton = Button.builder()
                .setIconName("view-refresh-symbolic")
                .setTooltipText("Restart Game")
                .setActionName("app.restart")
                .build();
        Button undoButton = Button.builder()
                .setIconName("edit-undo-symbolic")
                .setTooltipText("Undo")
                .setActionName("app.undo")
                .build();
        Button autoCompleteButton = Button.builder()
                .setIconName("go-last-symbolic")
                .setTooltipText("Auto-Complete")
                .setActionName("app.auto-complete")
                .build();
        autoCompleteButton.addCssClass("button_hint_glow");
        headerBar.packStart(newGameButton);
        headerBar.packStart(restartButton);
        headerBar.packStart(undoButton);
        headerBar.packStart(autoCompleteButton);

        gameTimer = new GameTimer();
        gameTimer.setOnToggleClicked(this::toggleTimer);
        headerBar.packStart(gameTimer.getWidget());

        Button preferencesButton = Button.builder()
                .setIconName("preferences-system-symbolic")
                .setTooltipText("Preferences")
                .setActionName("app.preferences")
                .build();
        Button aboutButton = Button.builder()
                .setIconName("help-about-symbolic")
                .setTooltipText("About")
                .setActionName("app.about")
                .build();
        Button helpButton = Button.builder()
                .setIconName("help-browser-symbolic")
                .setTooltipText("Help")
                .setActionName("app.help")
                .build();
        headerBar.packEnd(aboutButton);
        headerBar.packEnd(helpButton);
        headerBar.packEnd(preferencesButton);

        return headerBar;
    }


    /**
     * the header bar's play/pause button
     */
    private void toggleTimer() {
        if(isPaused) {
            resumeGame();
        } else {
            pauseGame();
        }
    }


    /**
     * pause the game: stop the clock, block every board interaction, and cover the board with a
     * frosted-glass overlay - so a player can step away without being able to keep quietly
     * studying the (still visible, just not timed) board state. A no-op once the game is won
     * (nothing left to pause), before the clock has ever started (nothing to pause on a freshly
     * dealt, untouched board either - see GameTimer.hasStarted()), or while a drag is in flight,
     * for the same reason undo() is blocked mid-drag elsewhere: disabling the board out from under
     * GTK's native drag machinery risks the 'real_box->magic == G_BOX_MAGIC' assertion crash
     * documented there.
     */
    private void pauseGame() {
        if(isPaused || game.isVictory() || ! gameTimer.hasStarted() || CardWidget.currentDragPayload != null) {
            return;
        }
        isPaused = true;
        gameTimer.pause();
        gameBoard.setSensitive(false);
        undoAction.setEnabled(false);
        autoCompleteAction.setEnabled(false);
        // otherwise a paused player could still open Preferences and quietly flip "Show timer" off
        preferencesAction.setEnabled(false);
        boardOverlay.addOverlay(pauseOverlay);
    }


    /**
     * resume a paused game: hide the overlay, re-enable the board, restart the clock. Bound to
     * both the play/pause button and a click anywhere on pauseOverlay itself (see
     * buildPauseOverlay()) - "click window to resume".
     */
    private void resumeGame() {
        if(! isPaused) {
            return;
        }
        isPaused = false;
        boardOverlay.removeOverlay(pauseOverlay);
        gameBoard.setSensitive(true);
        preferencesAction.setEnabled(true);
        // re-syncs Undo/Auto-Complete from the actual game state, rather than just flipping them
        // back on - pauseGame() force-disabled both regardless of whether they were enabled before
        updateActionStates();
        gameTimer.start();
    }


    /**
     * build the frosted-glass "paused" cover shown over the whole board while paused (see
     * pauseGame()). Reuses .card_pile_base's blur/border/shadow, same as victoryBanner, but -
     * unlike that small centered banner - is left at its default FILL alignment so GtkOverlay
     * stretches it to cover the entire board, fully hiding the position underneath.
     * @return the pause overlay widget
     */
    private Box buildPauseOverlay() {
        Image pauseIcon = Image.builder()
                .setIconName("media-playback-pause-symbolic")
                .setPixelSize(64)
                .build();
        Label pauseLabel = Label.builder()
                .setLabel("Paused - click anywhere to resume")
                .setJustify(Justification.CENTER)
                .setHalign(Align.CENTER)
                .build();
        pauseLabel.addCssClass("pause_overlay_label");

        Box content = Box.builder()
                .setOrientation(Orientation.VERTICAL)
                .setSpacing(12)
                .setHalign(Align.CENTER)
                .setValign(Align.CENTER)
                // overlay (below) is an unset-orientation (i.e. default HORIZONTAL) Box, which
                // packs a single non-expanding child at its natural size flush to the start -
                // content's own halign/valign=CENTER has nothing to centre within unless it also
                // claims the box's full area via expand, so it actually centers within the whole
                // (window-filling) pause overlay rather than sitting pinned to the left edge
                .setHexpand(true)
                .setVexpand(true)
                .build();
        content.append(pauseIcon);
        content.append(pauseLabel);

        Box overlay = Box.builder().build();
        overlay.addCssClass("card_pile_base");
        overlay.addCssClass("pause_overlay");
        overlay.append(content);

        GestureClick resumeClick = GestureClick.builder().build();
        resumeClick.onPressed((nPress, x, y) -> resumeGame());
        overlay.addController(resumeClick);

        return overlay;
    }


    /**
     * show the About dialogue
     */
    private void showAboutDialog() {
        String licenceText = Utilities.loadLicenseText();
        if(licenceText.isEmpty()) {
            licenceText = "MIT licenceText - file could not be read.";
        }
        AboutDialog aboutDialog = AboutDialog.builder()
                .setTransientFor(window)
                .setModal(true)
                // GtkAboutDialog's internal page stack is homogeneously sized by default, so
                // without a capped size the whole window grows to fit the tallest page (Credits,
                // once it has enough rows) even while showing the much shorter General page,
                // leaving that page's content stranded in the lower half; capping the size makes
                // the Credits page scroll internally instead of inflating the whole dialogue
                .setDefaultWidth(420)
                .setDefaultHeight(520)
                .setProgramName(APP_TITLE)
                .setVersion(APP_VERSION)
                .setComments("A simple Klondike Solitaire implementation using GTK4 and java-gi.")
                .setAuthors(new String[] {"@coffinwood", "A portion of the application was authored with the assistance of Claude (Anthropic)"})
                .setLicense(licenceText)
                .build();

        List<Attribution> attributions = Utilities.loadAttributions();
        if(! attributions.isEmpty()) {
            String[] credits = attributions.stream()
                    .map(Utilities::formatAttribution)
                    .toArray(String[]::new);
            aboutDialog.addCreditSection("Third-Party Assets", credits);
        }

        aboutDialog.present();
    }


    /**
     * show the Help dialogue: a scrollable tutorial / tips-and-tricks writeup
     */
    private void showHelpDialog() {
        String helpText = Utilities.loadResourceText("/help/help.txt");
        if(helpText.isEmpty()) {
            helpText = "Help text could not be read.";
        }

        Window dialog = Window.builder()
                .setTitle("Help")
                .setTransientFor(window)
                .setModal(true)
                .setDestroyWithParent(true)
                .setDefaultWidth(460)
                .setDefaultHeight(560)
                .build();

        Label helpLabel = Label.builder()
                .setLabel(helpText)
                .setUseMarkup(true)
                .setWrap(true)
                .setWrapMode(WrapMode.WORD_CHAR)
                .setJustify(Justification.LEFT)
                .setXalign(0)
                .setValign(Align.START)
                .build();

        ScrolledWindow scroller = ScrolledWindow.builder()
                .setChild(helpLabel)
                .setHscrollbarPolicy(PolicyType.NEVER)
                .setVscrollbarPolicy(PolicyType.AUTOMATIC)
                .setVexpand(true)
                .setMarginTop(16)
                .setMarginBottom(16)
                .setMarginStart(16)
                .setMarginEnd(16)
                .build();

        dialog.setChild(scroller);
        dialog.present();
    }


    /**
     * show the Preferences dialogue (card scale, draw amount, card back).
     * GtkDialog is deprecated as of GTK 4.10 ("Use Window instead" per upstream docs), so this
     * is a plain Gtk.Window with its own content box and close button instead of Dialogue's
     * response-signal machinery.
     */
    private void showPreferencesDialog() {
        Window dialog = Window.builder()
                .setTitle("Preferences")
                .setTransientFor(window)
                .setModal(true)
                .setDestroyWithParent(true)
                .build();

        Box content = Box.builder().setOrientation(Orientation.VERTICAL).build();
        dialog.setChild(content);
        content.setSpacing(12);
        content.setMarginTop(12);
        content.setMarginBottom(12);
        content.setMarginStart(12);
        content.setMarginEnd(12);

        content.append(Label.builder().setLabel("Card size").setHalign(Align.START).build());
        Scale scaleSlider = Scale.withRange(Orientation.HORIZONTAL, 0.5, 2.0, 0.1);
        scaleSlider.setValue(preferences.getCardScale());
        scaleSlider.setDrawValue(true);
        scaleSlider.setHexpand(true);
        // keep the GSettings key and the slider in sync automatically
        preferences.getSettings().bind("card-scale", scaleSlider.getAdjustment(), "value", SettingsBindFlags.DEFAULT);

        // applying live on every drag tick made the window jump around under the pointer and
        // fight the user for control of the slider; require an explicit click instead
        Button applyScaleButton = Button.builder().setLabel("Apply").setHalign(Align.END).build();
        applyScaleButton.onClicked(() -> applyCardScale(scaleSlider.getValue()));

        Box scaleRow = Box.builder().setOrientation(Orientation.HORIZONTAL).setSpacing(12).build();
        scaleRow.append(scaleSlider);
        scaleRow.append(applyScaleButton);
        content.append(scaleRow);

        content.append(Label.builder().setLabel("Draw amount").setHalign(Align.START).build());
        CheckButton drawOneRadio = CheckButton.builder().setLabel("Draw 1").build();
        CheckButton drawThreeRadio = CheckButton.builder().setLabel("Draw 3").setGroup(drawOneRadio).build();
        (preferences.getDrawAmount() == 3 ? drawThreeRadio : drawOneRadio).setActive(true);
        drawOneRadio.onToggled(() -> {
            if(drawOneRadio.getActive()) {
                preferences.setDrawAmount(1);
                game.setDrawAmount(1);
            }
        });
        drawThreeRadio.onToggled(() -> {
            if(drawThreeRadio.getActive()) {
                preferences.setDrawAmount(3);
                game.setDrawAmount(3);
            }
        });

        Box drawAmountRow = Box.builder().setOrientation(Orientation.HORIZONTAL).setSpacing(12).build();
        drawAmountRow.append(drawOneRadio);
        drawAmountRow.append(drawThreeRadio);
        content.append(drawAmountRow);

        content.append(Label.builder().setLabel("Card back").setHalign(Align.START).build());
        Box backRow = Box.builder().setOrientation(Orientation.HORIZONTAL).setSpacing(12).build();
        ToggleButton firstBackToggle = null;
        for(String backName : CardImages.BACK_TEXTURE_NAMES) {
            ToggleButton backToggle = buildBackTextureToggle(backName, firstBackToggle);
            if(firstBackToggle == null) {
                firstBackToggle = backToggle;
            }
            backToggle.setActive(backName.equals(preferences.getCardBack()));
            backToggle.onToggled(() -> {
                if(backToggle.getActive()) {
                    preferences.setCardBack(backName);
                    CardImages.setBackTexture(backName);
                    // gtk_widget_queue_draw() only walks UP a widget's ancestor chain, clearing
                    // each ancestor's cached render node so it recomposites - it never walks DOWN
                    // into children. window.queueDraw() therefore only invalidates the window's
                    // own cached node; every CardWidget deep in the pile tree keeps its last
                    // rendered (stale) snapshot and just gets recomposited unchanged, which is why
                    // the new back only ever showed up once something else (dealing a new game,
                    // flipping a card) queued *that specific* CardWidget for redraw. Each face-down
                    // card has to be invalidated individually instead.
                    for(PileWidget pileWidget : getAllPileWidgets()) {
                        for(CardWidget cardWidget : pileWidget.getCardWidgets()) {
                            if(! cardWidget.getCard().isFaceUp()) {
                                cardWidget.queueDraw();
                            }
                        }
                    }
                }
            });
            backRow.append(backToggle);
        }
        content.append(backRow);

        if(! backgroundClassNames.isEmpty()) {
            content.append(Label.builder().setLabel("Window background").setHalign(Align.START).build());

            String[] backgroundLabels = backgroundClassNames.stream()
                    .map(GTKlondike::formatBackgroundLabel)
                    .toArray(String[]::new);
            DropDown backgroundDropDown = DropDown.fromStrings(backgroundLabels);
            int currentIndex = backgroundClassNames.indexOf(preferences.getBackground());
            backgroundDropDown.setSelected(Math.max(currentIndex, 0));
            backgroundDropDown.onNotify("selected", pspec -> {
                String selectedBackground = backgroundClassNames.get(backgroundDropDown.getSelected());
                preferences.setBackground(selectedBackground);
                applyBackground(selectedBackground);
            });
            content.append(backgroundDropDown);
        }

        CheckButton showTimerCheck = CheckButton.builder().setLabel("Show timer in header bar").build();
        showTimerCheck.setActive(preferences.isShowTimer());
        // keep the GSettings key in sync automatically, same as the card-scale slider above
        preferences.getSettings().bind("show-timer", showTimerCheck, "active", SettingsBindFlags.DEFAULT);
        showTimerCheck.onToggled(() -> gameTimer.setVisible(showTimerCheck.getActive()));
        content.append(showTimerCheck);

        dialog.present();
    }


    /**
     * called once when the game is won: clear the board's pile/card widgets, disable
     * Redeal/Undo/Auto-Complete (there's nothing left to do with them)
     */
    private void celebrateVictory() {
        restartAction.setEnabled(false);
        undoAction.setEnabled(false);
        autoCompleteAction.setEnabled(false);
        newGameButton.addCssClass("button_hint_glow");
        // nothing left to time (or pause) once the game is won
        gameTimer.pause();
        gameTimer.setToggleButtonEnabled(false);
        pauseAction.setEnabled(false);

        int bannerWidth = (int) (gameBoard.getWidth() * VICTORY_BANNER_WIDTH_FRACTION);
        int bannerHeight = (int) (bannerWidth * VICTORY_BANNER_ASPECT_RATIO);
        victoryBanner.setSizeRequest(bannerWidth, bannerHeight);
        // Statistics.addGameWon() already ran (Game.checkForVictory() calls it just before
        // onVictory.run() fires this), so these reads include the win being celebrated right now
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault());
        victoryStatsLabel.setLabel(String.format(
                "So far, you've played %s moves and won %s out of your %s played games.",
                numberFormat.format(Statistics.getMoves()),
                numberFormat.format(Statistics.getGamesWon()),
                numberFormat.format(Statistics.getGamesPlayed())));
        // hide the finished board rather than leaving it visible behind the banner - board
        // widgets are permanent/pooled now (see BoardWidgets), so this hides it instead of the
        // boardOverlay.setChild(null) this used to be, which would have unparented (and thus
        // made Java-unreachable) every pile/card widget on it in one burst; onGameReplaced()
        // (New Game/Restart from here, or Undo) makes it visible again
        gameBoard.setVisible(false);
        boardOverlay.addOverlay(victoryOverlay);
    }


    /**
     * undo everything celebrateVictory() applied, e.g. after New Game/Restart - a no-op for
     * whichever parts aren't currently applied. Undo/Auto-Complete's enabled state - and the
     * board's visibility - are re-synced separately by onGameReplaced(), which both callers
     * already make right before this one
     */
    private void clearVictoryBanner() {
        restartAction.setEnabled(true);
        newGameButton.removeCssClass("button_hint_glow");
        gameTimer.setToggleButtonEnabled(true);
        pauseAction.setEnabled(true);
        if(victoryOverlay.getParent() != null) {
            boardOverlay.removeOverlay(victoryOverlay);
        }
    }


    /**
     * build a clickable thumbnail button for a card back texture; grouped toggle buttons behave
     * like radio buttons (mutually exclusive) and get a highlighted/pressed look while active
     * @param backTextureName one of CardImages.BACK_TEXTURE_NAMES
     * @param group an already-built toggle to join, or null to start a new group
     * @return toggle button showing the texture
     */
    private ToggleButton buildBackTextureToggle(String backTextureName, ToggleButton group) {
        Picture picture = Picture.forPaintable(CardImages.getBackTexture(backTextureName));
        picture.setCanShrink(true);
        picture.setContentFit(ContentFit.CONTAIN);

        ScrolledWindow thumbnailClamp = ScrolledWindow.builder()
                .setChild(picture)
                .setHscrollbarPolicy(PolicyType.AUTOMATIC)
                .setVscrollbarPolicy(PolicyType.AUTOMATIC)
                .setPropagateNaturalWidth(false)
                .setPropagateNaturalHeight(false)
                .setMinContentWidth(200)
                .setMinContentHeight(280)
                .build();

        ToggleButton toggle = ToggleButton.builder()
                .setChild(thumbnailClamp)
                .setTooltipText(backTextureName)
                .build();
        if(group != null) {
            toggle.setGroup(group);
        }
        return toggle;
    }


    /**
     * apply a card-size multiplier to every pile in the current game, live
     * @param scale card size multiplier
     */
    private void applyCardScale(double scale) {
        int cardWidth = (int) (BASE_CARD_WIDTH * scale);
        int cardHeight = (int) (cardWidth * 1.4);

        for(PileWidget pileWidget : getAllPileWidgets()) {
            pileWidget.getPileLayoutManager().setCardSize(cardWidth, cardHeight);
            pileWidget.queueResize();
        }
    }


    /**
     * every pile widget on the board (stock, waste, foundations, tableau) - these are permanent/
     * pooled now (see BoardWidgets), so this no longer needs to go through `game` at all
     * @return list of pile widgets
     */
    private List<PileWidget> getAllPileWidgets() {
        return boardWidgets.getAllPileWidgets();
    }


    /**
     * turn a CSS class name into a human-friendly dropdown label, e.g. "bg_trees" -> "Trees"
     * @param backgroundClassName a "bg_"-prefixed CSS class name
     * @return display label
     */
    private static String formatBackgroundLabel(String backgroundClassName) {
        String name = backgroundClassName.startsWith("bg_")
                ? backgroundClassName.substring(3)
                : backgroundClassName;
        name = name.replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }


    /**
     * switch the play board's background to the given "bg_" CSS class, removing whichever one
     * (if any) is currently applied to the window.
     * @param backgroundClassName one of backgroundClassNames
     */
    private void applyBackground(String backgroundClassName) {
        for(String cssClass : window.getCssClasses()) {
            if(cssClass.startsWith("bg_")) {
                window.removeCssClass(cssClass);
            }
        }
        window.addCssClass(backgroundClassName);
    }


    /**
     * let the piles' widgets be a drop target for drag and drop actions
     * @param targetSupplier move target
     */
    private void attachDropTarget(PileWidget widget, Supplier<MoveTarget> targetSupplier) {
        DropTarget dropTarget = DropTarget.builder()
                .setActions(DragAction.MOVE)
                .build();
        dropTarget.setGtypes(new Type[] { Types.INT }); // matches the marker Value type used in CardWidget.onPrepare
        dropTarget.onDrop((value, x, y) -> {
            CardRunPayload payload = CardWidget.currentDragPayload;
            if(payload == null) {
                return false;
            }
            // both `game` and the target pile resolved fresh on every drop, not captured here -
            // this is wired up once (see buildBoardOnce()) but outlives many New Game/Restart/
            // Undo cycles, each of which replaces `game`'s pile objects
            return game.tryMove(payload.cards(), payload.sourcePile(), targetSupplier.get());
        });
        widget.addController(dropTarget);
    }
}