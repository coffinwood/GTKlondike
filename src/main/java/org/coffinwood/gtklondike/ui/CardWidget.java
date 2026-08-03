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
    }


    /**
     * repurpose this already-constructed widget for a different card/pile, instead of
     * constructing a fresh CardWidget - see CardWidgetPool. setupDragSource()'s handlers read
     * `card`/`ownerPile` as instance fields (not captured locals), so they pick up the new
     * values automatically; nothing about the drag controller itself needs re-wiring.
     * @param card the (different) card this widget now represents
     * @param ownerPile the (possibly different) pile this widget now belongs to
     */
    public void rebind(Card card, MoveSource ownerPile) {
        this.card = card;
        this.ownerPile = ownerPile;
        // a widget can only be reused once fully detached (see CardWidgetPool.release()), which
        // never leaves it invisible - this just guards against reusing one still mid-drag-hide
        setVisible(true);
    }


    /**
     * return card
     * @return card
     */
    public Card getCard() {
        return card;
    }


    /**
     * the card must be draggable, so a drag-source
     */
    private void setupDragSource() {
        DragSource dragSource = DragSource.builder()
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

        addController(dragSource);
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
        // toPaintable(), not freeToPaintable(): the latter maps to gtk_snapshot_free_to_paintable,
        // whose naming/semantics match the "consumes its receiver" pattern that caused this
        // session's earlier Gsk.Transform native-memory-corruption bug. Snapshot is a plain
        // GObject here (not a boxed rc-type like Transform was) so it may well be fine either
        // way, but there's no reason to re-risk that class of bug for no benefit.
        return snapshot.toPaintable(new Size(width, height));
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
