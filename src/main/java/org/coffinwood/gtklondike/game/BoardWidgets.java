package org.coffinwood.gtklondike.game;

import org.coffinwood.gtklondike.ui.CardWidget;
import org.coffinwood.gtklondike.ui.PileWidget;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


/**
 * The board's native (GTK-backed) pile widgets. Created once for the application's lifetime and
 * reused across every New Game/Restart/Undo. Preferred to being torn down and rebuilt each time -
 * along with a matching CardWidget pool, since a deal/restart/undo replaces every pile's cards in
 * one burst.
 */
public class BoardWidgets {
    private final PileWidget stockWidget = PileWidget.create();
    private final PileWidget wasteWidget = PileWidget.create();
    private final List<PileWidget> tableauWidgets = new ArrayList<>(7);
    private final List<PileWidget> foundationWidgets = new ArrayList<>(4);
    // CardWidgets currently parented nowhere, available for reuse by obtainCardWidget()
    private final Deque<CardWidget> cardWidgetPool = new ArrayDeque<>();


    /**
     * constructor - creates all 13 persistent pile widgets up front
     */
    public BoardWidgets() {
        for(int lane = 0; lane < 7; lane++) {
            tableauWidgets.add(PileWidget.create());
        }
        for(int suit = 0; suit < 4; suit++) {
            foundationWidgets.add(PileWidget.create());
        }
    }


    public PileWidget getStockWidget() {
        return stockWidget;
    }


    public PileWidget getWasteWidget() {
        return wasteWidget;
    }


    public PileWidget getTableauWidget(int lane) {
        return tableauWidgets.get(lane);
    }


    public PileWidget getFoundationWidget(int suit) {
        return foundationWidgets.get(suit);
    }


    /**
     * every persistent pile widget, e.g. for attaching each into the board's Grid once at
     * startup or wiring up drop targets once
     * @return all 13 pile widgets
     */
    public List<PileWidget> getAllPileWidgets() {
        List<PileWidget> all = new ArrayList<>(13);
        all.add(stockWidget);
        all.add(wasteWidget);
        all.addAll(tableauWidgets);
        all.addAll(foundationWidgets);
        return all;
    }


    /**
     * hand back a CardWidget bound to the given card/pile - reused from the pool if one's
     * available, otherwise (only ever expected on the very first deal) freshly constructed
     * @param card card the widget should represent
     * @param ownerPile pile the widget belongs to
     * @return a CardWidget bound to card/ownerPile
     */
    public CardWidget obtainCardWidget(Card card, MoveSource ownerPile) {
        CardWidget widget = cardWidgetPool.poll();
        if(widget == null) {
            return new CardWidget(card, ownerPile);
        }
        widget.rebind(card, ownerPile);
        return widget;
    }


    /**
     * return an already-unparented CardWidget to the pool for reuse. Callers must unparent it
     * (e.g. via PileWidget.removeCard()) before calling this.
     * @param widget widget to pool
     */
    public void releaseCardWidget(CardWidget widget) {
        cardWidgetPool.push(widget);
    }


    /**
     * Unparent and pool every CardWidget currently attached to any pile widget - called once at
     * the start of a deal/restart/undo, before that operation starts pushing a fresh hand's cards
     * into these same, reused pile widgets. Whatever's left over from however the previous game
     * ended is scattered across all 13 piles. Not just the ones a normal move/draw would have
     * touched, so this has to sweep all of them rather than relying on the normal
     * push()/removeRunWidgets() bookkeeping.
     */
    public void reclaimAllCardWidgets() {
        for(PileWidget pileWidget : getAllPileWidgets()) {
            for(CardWidget cardWidget : new ArrayList<>(pileWidget.getCardWidgets())) {
                pileWidget.removeCard(cardWidget);
                releaseCardWidget(cardWidget);
            }
        }
    }
}
