package org.coffinwood.gtklondike.game;

import org.coffinwood.gtklondike.ui.CardWidget;
import org.coffinwood.gtklondike.ui.PileWidget;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


/**
 * pile of cards for the foundation (4 suits from Ace to King)
 */
public class FoundationPile implements MoveSource, MoveTarget {
    private final int suit;
    private final Deque<Card> cards = new ArrayDeque<>();
    private final PileWidget widget;
    private final BoardWidgets boardWidgets;


    /**
     * constructor
     * @param suit suit (also selects the persistent widget to reuse)
     * @param offsetY vertical pixel offset to render cards to be distinguishable
     * @param boardWidgets the board's persistent/pooled widgets
     */
    public FoundationPile(int suit, int offsetY, BoardWidgets boardWidgets) {
        this.suit = suit;
        this.boardWidgets = boardWidgets;
        this.widget = boardWidgets.getFoundationWidget(suit);
        widget.setWidgetOwner(this);
        widget.addCssClass("card_pile_base");
        widget.addCssClass("foundation_pile-" + suit);
        // Tableau-specific cascade offset, as opposed to stock/foundation's flush stacking
        widget.getPileLayoutManager().setCardOffsetY(offsetY);
    }


    /**
     * @param startCard card to start run
     * @return run of cards
     */
    @Override
    public List<Card> getRunStartingAt(Card startCard) {
        // cannot move face-down cards
        if(! startCard.isFaceUp()) {
            return null;
        }
        List<Card> run = new ArrayList<>();
        run.add(startCard);
        return run;
    }


    /**
     * add a run of cards, here it's always 1
     * @param run run of cards
     */
    @Override
    public void addRun(List<Card> run) {
        push(run.getFirst());
    }


    /**
     * remove a run of cards from the pile (data model only, widgets stay parented)
     * @param run run of cards
     */
    @Override
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
    @Override
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
     * return widget
     * @return this pile's widget
     */
    public PileWidget getWidget() {
        return widget;
    }


    /**
     * cam the provided card be placed onto the pile?
     * @param run run of cards
     * @return TRUE if the pile accepts the run as part of a legal move
     */
    public boolean canAcceptRun(List<Card> run) {
        // not if it's more than 1 card
        if(run.size() > 1) {
            return false;
        }
        Card card = run.getFirst();

        // not if wrong suit
        if(card.getSuit() != suit) {
            return false;
        }

        Card topCard = cards.peek();
        // foundation pile is empty and try to place other card than an Ace?
        if(topCard == null) {
            return card.getRank() == 0;
        }
        // pile not empty and try to place other card than one rank above top card on pile?
        return card.getRank() == topCard.getRank() + 1;
    }


    /**
     * add card to pile
     * @param card card
     */
    public void push(Card card) {
        cards.push(card);
        widget.addCard(boardWidgets.obtainCardWidget(card, this));
    }




    /**
     * look at top card
     * @return top card
     */
    public Card peek() {
        return cards.peek();
    }


    /**
     * is the pile empty?
     * @return TRUE if empty
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }


    /**
     * all cards currently on the pile, bottom (Ace) to top (highest played rank) - the same order
     * repeated push() calls would rebuild the pile in, since push() adds to the front of the deque
     * @return cards, bottom to top
     */
    public List<Card> getCards() {
        List<Card> bottomToTop = new ArrayList<>(cards);
        java.util.Collections.reverse(bottomToTop);
        return bottomToTop;
    }


    /**
     * check if this pile is full: if all 4 foundation piles are full =========>>> V-I-C-T-O-R-Y
     * @return TRUE, if the card pile is full
     */
    public boolean isFull() {
        return cards.size() == 13;
    }
}
