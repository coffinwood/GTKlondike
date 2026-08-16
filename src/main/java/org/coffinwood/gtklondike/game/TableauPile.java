package org.coffinwood.gtklondike.game;

import org.coffinwood.gtklondike.ui.CardWidget;
import org.coffinwood.gtklondike.ui.PileWidget;
import java.util.*;


/**
 * card pile on the tableau, there are 7 of them
 */
public class TableauPile implements MoveSource, MoveTarget {
    private final List<Card> cards = new ArrayList<>();
    private final PileWidget widget;
    private final BoardWidgets boardWidgets;


    /**
     * constructor
     * @param offsetY offset for fanning down cards visually
     * @param lane which of the 7 tableau lanes this is (selects the persistent widget to reuse)
     * @param boardWidgets the board's persistent/pooled widgets
     */
    public TableauPile(int offsetY, int lane, BoardWidgets boardWidgets) {
        this.boardWidgets = boardWidgets;
        widget = boardWidgets.getTableauWidget(lane);
        // Tableau-specific cascade offset, as opposed to stock/foundation's flush stacking
        widget.getPileLayoutManager().setCardOffsetY(offsetY);
        widget.setWidgetOwner(this);
    }


    /**
     * get pile's widget
     * @return widget
     */
    public PileWidget getWidget() {
        return widget;
    }


    /**
     * add card to pile
     * @param card card
     */
    public void push(Card card) {
        cards.add(card);
        CardWidget cardWidget = boardWidgets.obtainCardWidget(card, this);
        widget.addCard(cardWidget);
    }



    /**
     * look at current top card
     * @return top card
     */
    public Card peek() {
        return cards.isEmpty() ? null : cards.getLast();
    }


    /**
     * append an already-legal run (e.g. dragged from another tableau) onto this pile
     * @param run run of cards
     */
    public void addRun(List<Card> run) {
        for(Card card : run) {
            push(card);
        }
    }


    /**
     * remove card(s) from this tableau lane's pile (data model only, widgets stay parented)
     * @param run run of cards
     */
    public void removeRun(List<Card> run) {
        // remove data
        cards.removeAll(run);

        // turn the next card face up
        Card newTop = peek();
        if(newTop != null && !newTop.isFaceUp()) {
            newTop.setFaceUp(true);
            // repaint with face texture
            widget.getWidget(newTop).queueDraw();
        }
    }


    /**
     * detach the widgets for a run that has already been removed from the data model via removeRun
     * @param run run of cards
     */
    public void removeRunWidgets(List<Card> run) {
        for(Card card : run) {
            CardWidget cardWidget = widget.getWidget(card);
            if(cardWidget != null) {
                widget.removeCard(cardWidget);
                boardWidgets.releaseCardWidget(cardWidget);
            }
        }
    }


     /**
     * return all cards
     * @return cards
     */
    public List<Card> getCards() {
        return cards;
    }


    /**
     * is pile empty?
     * @return TRUE, if empty
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }


    /**
     * get a run of cards beginning at the selected card downwards
     * @param startCard card to start run at
     * @return run of cards
     */
    public List<Card> getRunStartingAt(Card startCard) {
        // cannot move face-down cards
        if(! startCard.isFaceUp()) {
            return null;
        }

        int startIndex = cards.indexOf(startCard);
        if (startIndex < 0) {
            return null;
        }

        return new ArrayList<>(cards.subList(startIndex, cards.size()));
    }


    /**
     * can the supplied run be placed on this pile?
     * must be either a King card on an empty pile or one rank and a colour difference
     * @param run run of cards
     * @return TRU if acceptable
     */
    public boolean canAcceptRun(List<Card> run) {
        // look at pile's top card
        Card top = peek();
        // look at run's top card
        Card incoming = run.getFirst();
        // if pile is empty, only accept King
        if(top == null) {
            return incoming.getRank() == 12; // empty column, King only
        }
        // only accept incoming run with a top card exactly one rank below current top card
        // and not the same colour
        return incoming.getRank() == top.getRank() - 1
                && incoming.isBlack() != top.isBlack();
    }
}
