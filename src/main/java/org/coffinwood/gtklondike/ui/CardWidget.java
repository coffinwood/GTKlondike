package org.coffinwood.gtklondike.ui;

import io.github.jwharm.javagi.gobject.types.Types;
import org.coffinwood.gtklondike.game.Card;
import org.coffinwood.gtklondike.game.CardRunPayload;
import org.coffinwood.gtklondike.game.MoveSource;
import org.coffinwood.gtklondike.game.SelectionManager;
import org.gnome.gdk.ContentProvider;
import org.gnome.gdk.DragAction;
import org.gnome.gdk.Paintable;
import org.gnome.gdk.Texture;
import org.gnome.gobject.Value;
import org.gnome.graphene.Size;
import org.gnome.gtk.DragSource;
import org.gnome.gtk.Snapshot;
import org.gnome.gtk.Widget;
import java.util.List;


/**
 * every card can be displayed in the UI and has to be represented by a widget
 */
public class CardWidget extends Widget {
    private Card card;
    private MoveSource ownerPile;
    private DragSource dragSource;
    // the current run is static because there can be only one at a time
    public static CardRunPayload currentDragPayload;


    /**
     * constructor
     * @param card corresponding play card
     * @param ownerPile pile to which the card belongs
     */
    public CardWidget(Card card, MoveSource ownerPile) {
        this.card = card;
        this.ownerPile = ownerPile;
        addCssClass("card_base");
        setupDragSource();
        updateDragSourceAttachment();
    }


    /**
     * repurpose this already-constructed widget for a different card/pile, instead of
     * constructing a fresh CardWidget - see CardWidgetPool. setupDragSource()'s handlers read
     * `card`/`ownerPile` as instance fields (not captured locals), so they pick up the new
     * values automatically; nothing about the drag controller's handlers needs re-wiring, only
     * whether it's attached at all (see updateDragSourceAttachment()).
     * @param card the (different) card this widget now represents
     * @param ownerPile the (possibly different) pile this widget now belongs to
     */
    public void rebind(Card card, MoveSource ownerPile) {
        this.card = card;
        this.ownerPile = ownerPile;
        // a widget can only be reused once fully detached (see CardWidgetPool.release()), which
        // never leaves it invisible - this just guards against reusing one still mid-drag-hide
        setVisible(true);
        // remove CSS classes
        removeCssClass("card_selected");
        removeCssClass("card_normal");
        updateDragSourceAttachment();
    }


    /**
     * Stock-pile cards are always handed ownerPile == null (see
     * BoardWidgets.obtainCardWidget()/StockPile) and can never be dragged - onPrepare already
     * returns null for them - but merely having a DragSource controller attached still arms
     * GTK's native drag-gesture machinery on every button press. Rapid clicking on the stock
     * pile was implicated (via debug-launcher repro, see run-gtklondike-debug.sh) in a native
     * GLib-CRITICAL ("g_atomic_ref_count_dec"/"gdk_drop_finalize"-family) heap-corruption crash,
     * so widgets that can never actually be dragged shouldn't carry a DragSource at all. Since
     * CardWidgets are pooled and reused across every pile type (BoardWidgets.cardWidgetPool),
     * this has to be re-evaluated on every rebind(), not just at construction.
     */
    private void updateDragSourceAttachment() {
        boolean shouldBeAttached = ownerPile != null;
        boolean isAttached = dragSource.getWidget() != null;
        if(shouldBeAttached && ! isAttached) {
            addController(dragSource);
        } else if(! shouldBeAttached && isAttached) {
            removeController(dragSource);
        }
    }


    /**
     * return card
     * @return card
     */
    public Card getCard() {
        return card;
    }


    /**
     * build the drag-source controller and wire up its handlers. Attaching/detaching it to this
     * widget is handled separately by updateDragSourceAttachment().
     */
    private void setupDragSource() {
        dragSource = DragSource.builder()
                .setActions(DragAction.MOVE)
                .build();

        dragSource.onPrepare((x, y) -> {
            // face-down cards aren't draggable
            // empty piles aren't either
            if(! card.isFaceUp() || ownerPile == null) {
                return null;
            }

            // move one or more cards
            List<Card> run = ownerPile.getRunStartingAt(card);
            // e.g. invalid run
            if(run == null) {
                return null;
            }

            // overwrite the previous drag payload with the current one
            currentDragPayload = new CardRunPayload(run, ownerPile);
            // Marker content — real payload is read from currentDragPayload on drop
            Value value = new Value().init(Types.INT);
            value.setInt(1);
            return ContentProvider.forValue(value);
        });

        dragSource.onDragBegin(drag -> {
            SelectionManager.clearSelection();

            // Optional: set a custom drag icon showing the stacked run
            Paintable icon = buildRunDragIcon(currentDragPayload.cards());
            dragSource.setIcon(icon, 0, 0);

            // Hide the dragged widgets from their original pile during the drag
            for(Card card : currentDragPayload.cards()) {
                CardWidget cardWidget = ownerPile.getWidget().getWidget(card);
                if(cardWidget != null) {
                    cardWidget.setVisible(false);
                }
            }
        });

        dragSource.onDragEnd((drag, deleteData) -> {
            // deleteData is true once GTK has accepted the MOVE and the drag sequence is
            // fully finished - only now is it safe to unparent the stale source widgets.
            // Doing this earlier (e.g. from the drop target's onDrop) unparents a widget
            // while GDK's native drag machinery still references it, corrupting its
            // ref-counted box (assertion 'real_box->magic == G_BOX_MAGIC' failed).
            if(deleteData) {
                ownerPile.removeRunWidgets(currentDragPayload.cards());
            } else {
                // cancelled/rejected: restore visibility instead
                for (Card card : currentDragPayload.cards()) {
                    CardWidget cardWidget = ownerPile.getWidget().getWidget(card);
                    if(cardWidget != null) {
                        cardWidget.setVisible(true);
                    }
                }
            }
            currentDragPayload = null;
        });
    }


    /**
     * Renders the top card's face texture at this widget's *current* on-screen size (not the
     * source PNG's native pixel size, e.g. 250x363 - returning the raw Texture directly made the
     * drag icon render at that native size instead of matching the actual card size, which is
     * further scaled by the user's card-scale preference on top of that).
     * @param cards list of cards
     * @return a Paintable sized to match the dragged card as it currently appears on the board
     */
    private Paintable buildRunDragIcon(List<Card> cards) {
        Texture texture = CardImages.getFaceTexture(cards.getFirst());
        int width = getWidth();
        int height = getHeight();

        Snapshot snapshot = new Snapshot();
        texture.snapshot(snapshot, width, height);
        // Unlike GObjects java-gi hands back from native code, `new Snapshot()` never gets a
        // toggle-ref/Cleaner registered (InstanceCache.newGObject() doesn't call put()), so
        // nothing ever frees this on its own
        Paintable paintable = snapshot.toPaintable(new Size(width, height));
        snapshot.unref();
        return paintable;
    }


    /**
     * actually draw the card
     * @param snapshot ask me something better^^
     */
    @Override
    public void snapshot(Snapshot snapshot) {
        Texture texture = card.isFaceUp() ? CardImages.getFaceTexture(card) : CardImages.getBackTexture();
        // TODO this shouldn't happen
        if(texture == null) {
            return;
        }

        texture.snapshot(snapshot, getWidth(), getHeight());
    }
}
