package org.coffinwood.gtklondike.ui;

import org.gnome.glib.GLib;
import org.gnome.glib.Source;
import org.gnome.gtk.Box;
import org.gnome.gtk.Button;
import org.gnome.gtk.Label;
import org.gnome.gtk.Orientation;


/**
 * the header bar's elapsed-time display: a "M:SS" readout plus a play/pause button, self-contained
 * as its own ticking GLib timeout so the owning window only has to call start()/pause()/reset() and
 * doesn't need to know how the clock is actually driven. The play/pause button itself only reports
 * clicks via {@link #setOnToggleClicked}; it deliberately does not call start()/pause() on itself,
 * since GTKlondike's own pause/resume also has to block board input and show an overlay - by
 * routing the click back out, GTKlondike's handler stays the single place deciding whether "pause"
 * is currently allowed at all (e.g. not while a drag is in flight or the game is already won).
 */
public class GameTimer {
    private final Box widget;
    private final Label timeLabel;
    private final Button toggleButton;
    private int elapsedSeconds;
    // GLib timeout source id for the running per-second tick, or 0 while paused/not yet started
    private int sourceId;
    private boolean running;
    // TRUE from the first start() after construction/reset() onward, even while currently paused
    // (unlike `running`) - lets the owner tell "paused mid-game" apart from "never started yet",
    // e.g. to refuse pausing a freshly dealt board that hasn't been played at all
    private boolean hasStarted;


    /**
     * build the timer's widgets; visible immediately, showing "0:00" and not yet running - call
     * start() to actually begin ticking
     */
    public GameTimer() {
        timeLabel = Label.builder().build();
        timeLabel.addCssClass("timer_label");

        // a real (disabled) Button rather than a plain Label: GTK renders it with its own
        // "insensitive button" look for free, which reads as a persistent status field sitting in
        // the header bar rather than an active control - making it obvious the clock is there even
        // before a game has started or while nothing is running
        Button display = Button.builder().setChild(timeLabel).build();
        display.setSensitive(false);
        display.addCssClass("timer_display");

        toggleButton = Button.builder().build();

        widget = Box.builder().setOrientation(Orientation.HORIZONTAL).setSpacing(6).build();
        widget.append(display);
        widget.append(toggleButton);

        updateLabel();
        showIdleIcon();
    }


    /**
     * the Box to pack into the header bar
     * @return the timer's top-level widget
     */
    public Box getWidget() {
        return widget;
    }


    /**
     * called whenever the play/pause button is clicked; see the class javadoc for why this is a
     * callback rather than GameTimer just toggling itself
     * @param callback invoked on every click of the play/pause button
     */
    public void setOnToggleClicked(Runnable callback) {
        toggleButton.onClicked(callback::run);
    }


    /**
     * enable/disable the play/pause button itself, e.g. once the game is won and there's nothing
     * left to pause
     * @param enabled TRUE to make the button clickable
     */
    public void setToggleButtonEnabled(boolean enabled) {
        toggleButton.setSensitive(enabled);
    }


    /**
     * show/hide the whole timer display, e.g. per the "Show timer" preference; the clock keeps
     * running either way - only its visibility changes
     * @param visible TRUE to show the timer
     */
    public void setVisible(boolean visible) {
        widget.setVisible(visible);
    }


    /**
     * stop (if running) and zero the clock, but - unlike an earlier version of this class - does
     * NOT start it back up: New Game/Restart/app launch all hand the player a freshly dealt board
     * that hasn't been touched yet, and starting the clock right then timed the player's very
     * first look at the board rather than their play. The caller is expected to call start() once
     * an actual move happens (see GTKlondike's lazy-start via Game.onGameStateChanged).
     */
    public void reset() {
        stopSource();
        elapsedSeconds = 0;
        hasStarted = false;
        updateLabel();
        showIdleIcon();
    }


    /**
     * (re)start the once-per-second tick; a no-op if it's already running
     */
    public void start() {
        if(running) {
            return;
        }
        running = true;
        hasStarted = true;
        sourceId = GLib.timeoutAddSeconds(GLib.PRIORITY_DEFAULT, 1, this::onTick);
        toggleButton.setIconName("media-playback-pause-symbolic");
        toggleButton.setTooltipText("Pause Timer");
    }


    /**
     * has the clock ticked at least once since construction/the last reset()? FALSE for a freshly
     * dealt board that hasn't had a move/draw yet (see GTKlondike's lazy-start) - stays TRUE
     * across a pause() (unlike the internal "running" state), so it distinguishes "never started"
     * from "currently paused mid-game"
     * @return TRUE once start() has run at least once since the last reset()
     */
    public boolean hasStarted() {
        return hasStarted;
    }


    /**
     * stop the once-per-second tick, keeping the elapsed time as-is; a no-op if already paused
     */
    public void pause() {
        if(! running) {
            return;
        }
        stopSource();
        showIdleIcon();
    }


    /**
     * set the toggle button to its "not currently ticking" look (play icon, "Resume Timer"
     * tooltip) - shared by pause(), reset(), and the constructor, so the button never shows a
     * stale "Pause Timer" state while the clock isn't actually running
     */
    private void showIdleIcon() {
        toggleButton.setIconName("media-playback-start-symbolic");
        toggleButton.setTooltipText("Resume Timer");
    }


    /**
     * GLib timeout callback: advance the clock by one second
     * @return TRUE (GLib.SOURCE_CONTINUE) so the timeout keeps repeating
     */
    private boolean onTick() {
        elapsedSeconds++;
        updateLabel();
        return true;
    }


    /**
     * remove the running GLib timeout source (if any) without touching elapsedSeconds or the
     * button's icon/tooltip - a helper shared by pause() and reset() (each sets the icon/tooltip
     * separately afterward)
     */
    private void stopSource() {
        if(sourceId != 0) {
            Source.remove(sourceId);
            sourceId = 0;
        }
        running = false;
    }


    /**
     * format elapsedSeconds as "M:SS" (minutes uncapped, seconds always zero-padded to 2 digits)
     */
    private void updateLabel() {
        timeLabel.setLabel("%d:%02d".formatted(elapsedSeconds / 60, elapsedSeconds % 60));
    }
}
