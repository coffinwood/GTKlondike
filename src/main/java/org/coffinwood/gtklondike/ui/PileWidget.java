package org.coffinwood.gtklondike.ui;

import io.github.jwharm.javagi.gobject.annotations.ClassInit;
import io.github.jwharm.javagi.gobject.types.Types;
import org.coffinwood.gtklondike.game.Card;
import org.coffinwood.gtklondike.game.MoveSource;
import org.coffinwood.gtklondike.game.MoveTarget;
import org.coffinwood.gtklondike.game.SelectionManager;
import org.coffinwood.gtklondike.game.StockPile;
import org.gnome.gdk.Gdk;
import org.gnome.glib.Type;
import org.gnome.gobject.GObject;
import org.gnome.gtk.GestureClick;
import org.gnome.gtk.PickFlags;
import org.gnome.gtk.Widget;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;


/**
 * UI widget representing a card pile
 */
public class PileWidget extends Widget {
    public static final Type gtype = Types.register(PileWidget.class);
    private final List<CardWidget> children = new ArrayList<>();
    private Object widgetOwner; // the pile object (MoveSource and/or MoveTarget) this widget belongs to


    /**
     * initialise the widget
     * @return this widget
     */
    public static PileWidget create() {
        PileWidget pileWidget = GObject.newInstance(gtype);
        // GObject.newInstance constructs the Java wrapper via a two-phase process (a stub via the
        // MemorySegment constructor, then the actual native allocation), so the no-arg constructor
        // body never runs against a fully-backed widget - CSS classes have to be applied here instead
        pileWidget.addCssClass("card_pile");
        // wired up once per widget, not per owner - see setWidgetOwner()
        pileWidget.setupClickGesture();
        return pileWidget;
    }


    /**
     * link this widget back to its owning pile. Widgets are pooled/reused across games (see
     * CardWidgetPool/GTKlondike's PileWidget pool), so this can be called more than once over a
     * widget's lifetime as it's handed to successive TableauPile/FoundationPile/WastePile/
     * StockPile instances; setupClickGesture() (which owns the only GestureClick this widget ever
     * gets) already reads `widgetOwner` as an instance field at click-time, so it doesn't need to
     * be, and must not be, re-run each time - doing so would stack up one more GestureClick
     * controller per game
     * @param widgetOwner the TableauPile/FoundationPile/WastePile/StockPile this widget represents
     */
    public void setWidgetOwner(Object widgetOwner) {
        this.widgetOwner = widgetOwner;
    }


    /**
     * this widget's PileLayoutManager, guaranteed non-null.
     */
    public PileLayoutManager getPileLayoutManager() {
        PileLayoutManager layoutManager = (PileLayoutManager) getLayoutManager();
        if(layoutManager == null) {
            layoutManager = GObject.newInstance(PileLayoutManager.gtype);
            setLayoutManager(layoutManager);
        }
        return layoutManager;
    }


    /**
     * constructor
     * NOTE this is never used but remains here as this class extends Widget, and we want to stick to "etiquette"
     */
    public PileWidget() {
        super();
    }


    /**
     * Required by java-gi's TypeCache so native pointers of this GType marshal back to
     * PileWidget instead of falling back to the generic Widget.WidgetImpl wrapper
     * NOTE see other constructor
     */
    public PileWidget(MemorySegment address) {
        super(address);
    }


    /**
     * Called once during type registration, via the @ClassInit annotation
     * NOTE see constructor
     * @param typeClass no idea^^
     */
    @ClassInit
    public static void classInit(Widget.WidgetClass typeClass) {
        typeClass.setLayoutManagerType(PileLayoutManager.gtype);
    }


    /**
     * add a card widget to pile
     * @param cardWidget card widget
     */
    public void addCard(CardWidget cardWidget) {
        cardWidget.setParent(this);
        children.add(cardWidget);
        queueAllocate();
    }


    /**
     * remove a card widget from the pile
     * @param cardWidget card widget
     */
    public void removeCard(CardWidget cardWidget) {
        children.remove(cardWidget);
        cardWidget.unparent();
        queueAllocate();
    }


    /**
     * return widget for given card
     * @param card card
     * @return widget of the corresponding card
     */
    public CardWidget getWidget(Card card) {
        for(CardWidget cardWidget : children) {
            if(cardWidget.getCard() == card) {
                return cardWidget;
            }
        }
        return null;
    }


    /**
     * get all widgets
     * @return list of widgets
     */
    public List<CardWidget> getCardWidgets() {
        return children;
    }


    /**
     * GTK4 widgets must unparent children explicitly on dispose. Must then chain up to the
     * parent class' dispose (GObject.dispose()'s own Javadoc: "Before returning, dispose should
     * chain up to the dispose method of the parent class"). Java-gi's override mechanism
     * replaces the whole class' dispose vfunc with this method, so skipping super.dispose() here
     * means GtkWidget's own base disposal (CSS node teardown, controller/layout-manager clean-up,
     * unrooting) never runs, silently corrupting GObject's bookkeeping. That surfaced as
     * "g_object_remove_toggle_ref: assertion 'G_IS_OBJECT (object)' failed" once undo started
     * replacing the whole board (and thus disposing every PileWidget) after cards had been dealt.
     */
    public void dispose() {
        Widget child = getFirstChild();
        while(child != null) {
            Widget next = child.getNextSibling();
            child.unparent();
            child = next;
        }
        super.dispose();
    }


    /**
     * make the pile clickable for selection clicks and double clicks; a right click is treated the
     * same as a double click (a quick way to send a card to its foundation without lining up two
     * quick left clicks)
     */
    private void setupClickGesture() {
        GestureClick click = GestureClick.builder().build();
        // 0 = listen for every button, not just the primary (left) one, so right clicks reach
        // this handler at all
        click.setButton(0);
        click.onPressed((nPress, x, y) -> {
            Widget hitWidget = pick(x, y, PickFlags.DEFAULT);
            Card hitCard = (hitWidget instanceof CardWidget cardWidget) ? cardWidget.getCard() : null;

            MoveSource source = (widgetOwner instanceof MoveSource moveSource) ? moveSource : null;
            MoveTarget target = (widgetOwner instanceof MoveTarget moveTarget) ? moveTarget : null;
            boolean isRightClick = click.getCurrentButton() == Gdk.BUTTON_SECONDARY;

            // any left click(s) on the stock pile will draw a card - right click is a no-op here,
            // there's nothing for "double click" to stand in for on the stock pile
            if(widgetOwner instanceof StockPile) {
                if(! isRightClick) {
                    SelectionManager.handleClickOnStockPile();
                }
            }
            // double click, or right click standing in for one
            else if(nPress == 2 || isRightClick) {
                SelectionManager.handleDoubleClick(hitCard, source);
            }
            // single (left) click
            else if(nPress == 1) {
                SelectionManager.handleClick(hitCard, source, target);
            }
        });
        addController(click);
    }
}
