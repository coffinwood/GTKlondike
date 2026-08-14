package org.coffinwood.gtklondike.ui;

import io.github.jwharm.javagi.base.Out;
import io.github.jwharm.javagi.gobject.types.Types;
import io.github.jwharm.javagi.interop.MemoryCleaner;
import org.gnome.glib.Type;
import org.gnome.graphene.Point;
import org.gnome.gtk.LayoutManager;
import org.gnome.gtk.Orientation;
import org.gnome.gtk.Widget;
import org.gnome.gsk.Transform;
import java.lang.foreign.MemorySegment;

/**
 * a custom layout manager for GTK that allows overlapping and stacking of cards in the UI to enable the classic
 * Solitaire look
 */
public class PileLayoutManager extends LayoutManager {
    public static final Type gtype = Types.register(PileLayoutManager.class);

    /**
     * cascade piles (cardOffsetY > 0) always report their natural/minimum size as if they held
     * exactly this many cards - NOT however many they actually, currently hold. A fresh tableau
     * lane starts with at most 7, so this leaves headroom for ordinary play before allocate()'s
     * shrink-to-fit (below) needs to compress a lane's card spacing to stay within it.
     *
     * This must stay a fixed constant, not track the live card count (not even capped): GTK grows
     * a window to fit a larger natural size but never shrinks it back down on its own, and
     * different tableau lanes grow/shrink at different rates as cards move between them during
     * play. A size that tracks the live count - even capped - still fluctuates below that cap as
     * cards come and go, and each increase drags the window up a notch it never comes back down
     * from. Reporting a constant here means the window settles on one size and stays there;
     * whatever a lane actually holds beyond this is handled entirely by allocate()'s squeeze.
     */
    private static final int MAX_CASCADE_CARDS_FOR_SIZING = 10;

    private double cardOffsetY = 0.0;   // 0 = stacked (stock/foundation), >0 = cascade (tableau)
    private double cardOffsetX = 0.0;   // 0 = stacked, >0 = fan (waste pile in draw-3 mode)
    // how many of the most-recently-added (trailing) children get spread out by cardOffsetX;
    // cards before that stay flush at x=0 (see frontOffsetX below for how they still peek out).
    // 0/1 => no extra spread within that window itself, which is what naturally keeps draw-1 mode
    // (batches of 1) from ever fanning
    private int fanCount = 0;
    // constant rightward offset applied to the whole trailing fanCount window, regardless of how
    // many older/covered cards sit behind it. Cards behind the window stay flush at x=0, so this
    // is the only thing that separates them from the front window - i.e. a single card's worth of
    // "peek" reveals that something's underneath, no matter how many cards actually are. Being a
    // fixed constant (not scaled by however many covered cards there are) keeps the front card's
    // position stable as the pile grows/shrinks - only a covered card's presence/absence changes,
    // never the front card's own offset, so it never visually "jumps". 0 = no effect, which is why
    // tableau/stock/foundation piles (which never call setFrontOffsetX) are unaffected
    private double frontOffsetX = 0.0;
    private int cardWidth = 150;
    private int cardHeight = (int)(cardWidth * 1.4);


    /**
     * constructor
     * NOTE this is never used but remains here as this class extends LayoutManager
     */
    public PileLayoutManager() {
        super();
    }


    /**
     * Required by java-gi's TypeCache so native pointers of this GType marshal back to
     * PileLayoutManager instead of falling back to the generic LayoutManager.LayoutManagerImpl wrapper
     * NOTE other constructor
     * @param address memory address
     */
    public PileLayoutManager(MemorySegment address) {
        super(address);
    }


    /**
     * set the offset in pixels by which cards in a stack are placed visually
     * @param offset offset
     */
    public void setCardOffsetY(double offset) {
        this.cardOffsetY = offset;
    }


    /**
     * set the offset in pixels by which the trailing {@link #setFanCount fanned} cards are spread
     * out horizontally
     * @param offset offset
     */
    public void setCardOffsetX(double offset) {
        this.cardOffsetX = offset;
    }


    /**
     * set how many of the most-recently-added cards should currently be spread out by the
     * horizontal offset; e.g. after drawing 3 cards in draw-3 mode, pass 3 so just that batch
     * fans out while any older, already-covered cards stay flush behind them. Clamped to the
     * pile's actual current card count at layout time only as a safety net (e.g. once the pile
     * shrinks below the fan itself, near the end of a game) - the caller is still responsible for
     * lowering this as cards get played off the top of the fanned batch (see WastePile.removeRun()),
     * since this class has no way to tell "the batch shrank" apart from "an older, already-covered
     * batch sits closer to the end of the pile than it used to" - both just look like a smaller
     * childCount, but only the former should ever grow the fan's window into older cards
     * @param fanCount trailing card count to fan out
     */
    public void setFanCount(int fanCount) {
        this.fanCount = fanCount;
    }


    /**
     * set the constant offset in pixels by which the trailing {@link #setFanCount fanned} window
     * is shifted right of any older/covered cards behind it (which stay flush at x=0) - much
     * smaller than {@link #setCardOffsetX}, just enough to let one card's worth of "peek" show
     * through on the left when there's anything behind the front window at all
     * @param offset offset
     */
    public void setFrontOffsetX(double offset) {
        this.frontOffsetX = offset;
    }


    /**
     * set the cards' widths and heights to allow scaling
     * @param width width
     * @param height height
     */
    public void setCardSize(int width, int height) {
        this.cardWidth = width;
        this.cardHeight = height;
    }


    /**
     * measure the place needed by a pile of cards
     * @param widget the `GtkWidget` using this LayoutManager
     * @param orientation the orientation to measure
     * @param forSize Size for the opposite of `orientation;` for instance, if
     *   the `orientation` is [org.gnome.gtk.Orientation#HORIZONTAL], this is the height
     *   of the widget; if the `orientation` is [org.gnome.gtk.Orientation#VERTICAL], this
     *   is the width of the widget. This allows to measure the height for the
     *   given width, and the width for the given height. Use -1 if the size
     *   is not known
     * @param minimum the minimum size for the given size and
     *   orientation
     * @param natural the natural, or preferred size for the
     *   given size and orientation
     * @param minimumBaseline the baseline position for the
     *   minimum size
     * @param naturalBaseline the baseline position for the
     *   natural size
     */
    @Override
    public void measure(Widget widget, Orientation orientation, int forSize,
                        Out<Integer> minimum, Out<Integer> natural,
                        Out<Integer> minimumBaseline, Out<Integer> naturalBaseline) {

        int childCount = countVisibleChildren(widget);
        int size;

        // horizontal layout for stock and discard pile. frontOffsetX is reserved unconditionally
        // (not just once a covered card actually exists behind it) so the front card's own position
        // never depends on the pile's size - see the field comment on frontOffsetX
        if(orientation == Orientation.HORIZONTAL) {
            int sizingFanCount = Math.min(fanCount, childCount);
            size = cardWidth + (int) (frontOffsetX + Math.max(0, sizingFanCount - 1) * cardOffsetX);
        }
        // vertical layout for tableau lanes
        else {
            int sizingChildCount = cardOffsetY > 0 ? MAX_CASCADE_CARDS_FOR_SIZING : childCount;
            size = cardHeight + (int) (Math.max(0, sizingChildCount - 1) * cardOffsetY);
        }

        minimum.set(size);
        natural.set(size);
        minimumBaseline.set(-1);
        naturalBaseline.set(-1);
    }


    /**
     * count widget's children to determine pile height
     * @param widget the widget
     * @return number of widget's children
     */
    private int countVisibleChildren(Widget widget) {
        int count = 0;
        Widget childWidget = widget.getFirstChild();
        // traverse through widget's children
        while(childWidget != null) {
            if(childWidget.getVisible()) {
                count++;
            }
            childWidget = childWidget.getNextSibling();
        }
        return count;
    }


    /**
     *
     * @param widget the `GtkWidget` using this LayoutManager
     * @param width the new width of the `widget`
     * @param height the new height of the `widget`
     * @param baseline the baseline position of the `widget,` or -1
     */
    @Override
    public void allocate(Widget widget, int width, int height, int baseline) {
        int childCount = countVisibleChildren(widget);

        double effectiveOffsetY = cardOffsetY;
        if(childCount > 1) {
            double neededHeight = cardHeight + (childCount - 1) * cardOffsetY;
            // height exceeds available space => shrink to fit
            if(neededHeight > height) {
                effectiveOffsetY = Math.max(0, (double) (height - cardHeight) / (childCount - 1));
            }
        }

        // at least 1 (once there's any card at all) so the top card always lands in the "front"
        // window below and gets frontOffsetX, regardless of fanCount - fanCount can legitimately be
        // 0 (e.g. WastePile's fanRemaining reaching 0 once the current draw batch is fully played
        // off), but there's always still a top card resting at the front, batch or no batch
        int effectiveFanCount = childCount > 0 ? Math.max(1, Math.min(fanCount, childCount)) : 0;
        // children before this index are older/already-covered cards - they stay flush at x=0;
        // only the trailing fanCount children (the current draw batch) get frontOffsetX plus the
        // full cardOffsetX spread
        int fanStartIndex = childCount - effectiveFanCount;

        double effectiveOffsetX = cardOffsetX;
        if(effectiveFanCount > 1) {
            double neededWidth = frontOffsetX + cardWidth + (effectiveFanCount - 1) * cardOffsetX;
            // fan exceeds available space => shrink to fit, same idea as the Y cascade above
            if(neededWidth > width) {
                effectiveOffsetX = Math.max(0, (width - frontOffsetX - cardWidth) / (effectiveFanCount - 1));
            }
        }

        Widget child = widget.getFirstChild();
        int index = 0;
        // tracks the most recently allocated card so its "card_shadow" class can be dropped if
        // the next card turns out to land in the exact same spot (i.e. fully covers it) - a
        // card's own box-shadow is pointless once nothing of it is visible, and worse, it still
        // composites underneath every card stacked on top of it
        Widget previousChild = null;
        double previousX = 0.0, previousY = 0.0;

        while(child != null) {
            if(child.getVisible()) {
                double y = index * effectiveOffsetY;
                double x = index >= fanStartIndex
                        ? frontOffsetX + (index - fanStartIndex) * effectiveOffsetX
                        : 0.0;

                child.addCssClass("card_shadow");
                if(previousChild != null && x == previousX && y == previousY) {
                    previousChild.removeCssClass("card_shadow");
                }
                previousChild = child;
                previousX = x;
                previousY = y;

                // x=0,y=0 (the front-most/only card in a freshly-emptied pile, or any pile with
                // cardOffsetX/Y of 0) skips GskTransform entirely rather than building then
                // immediately discarding an identity translation - gtk_widget_allocate() already
                // treats a null transform as identity, and this sidesteps a whole class of
                // native-reference-ownership bugs around GskTransform (see git history/comments
                // on this method for the two distinct ones already hit here) simply by never
                // creating the object that class of bug lives in, for what's also the single most
                // common case (every pile's first card hits this every allocate() pass).
                if(x == 0.0 && y == 0.0) {
                    child.allocate(cardWidth, cardHeight, baseline, null);
                } else {
                    Transform identity = new Transform();
                    Transform transform = identity.translate(new Point((float) x, (float) y));
                    // Two separate native consumptions happen here, and java-gi's binding never
                    // yields Java-side ownership for either, so the JVM's GC-triggered cleaner ends
                    // up double-freeing whatever it still thinks it owns:
                    //  1. gsk_transform_translate() consumes identity's reference.
                    //  2. gtk_widget_allocate() (child.allocate() below) is transfer-full on its
                    //     transform argument, i.e. it consumes transform's reference too.
                    // Left unfixed this surfaces as "g_atomic_rc_box_release_full: assertion
                    // 'real_box->magic == G_BOX_MAGIC' failed" or "g_atomic_ref_count_dec: assertion
                    // 'old_value > 0' failed", and the corruption it leaves behind can crash
                    // somewhere else entirely, later - reproducible just by growing a pile's card
                    // count (each added card re-triggers allocate() for every child in the pile).
                    MemoryCleaner.yieldOwnership(identity);
                    if(transform != identity) {
                        MemoryCleaner.yieldOwnership(transform);
                    }
                    child.allocate(cardWidth, cardHeight, baseline, transform);
                }
                index++;
            }
            child = child.getNextSibling();
        }
    }
}
