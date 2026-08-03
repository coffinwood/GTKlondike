package org.coffinwood.gtklondike.game;

import org.coffinwood.gtklondike.ui.CardWidget;
import org.coffinwood.gtklondike.ui.PileWidget;
import java.util.*;


/**
 * card pile to draw cards from
 */
public class StockPile {
    private Deque<Card> cards;
    private PileWidget widget;
    private final BoardWidgets boardWidgets;


    /**
     * constructor
     * @param cards cards to fill the stock pile with
     * @param boardWidgets the board's persistent/pooled widgets
     */
    public StockPile(ArrayList<Card> cards, BoardWidgets boardWidgets) {
        this.cards = new ArrayDeque<>(cards);
        this.boardWidgets = boardWidgets;
        widget = boardWidgets.getStockWidget(); // offset 0, default
        // Tableau-specific cascade offset, as opposed to stock/foundation's flush stacking
        widget.getPileLayoutManager().setCardOffsetY(0);

        for(Card card : this.cards) {
            widget.addCard(boardWidgets.obtainCardWidget(card, null));
        }
        widget.setWidgetOwner(this);
        widget.addCssClass("card_pile_base");
    }


    /**
     * get card pile's widget
     * @return widget
     */
    public PileWidget getWidget() {
        return widget;
    }


    /**
     * is card pile empty?
     * @return TRUE, if pile is empty
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }


    /**
     * draw a card and remove it from the pile
     * @return card
     */
    public Card draw() {
        Card card = cards.pop();
        CardWidget cardWidget = widget.getWidget(card);
        if(cardWidget != null) {
            widget.removeCard(cardWidget);
            boardWidgets.releaseCardWidget(cardWidget);
        }
        return card;
    }


    /**
     * add a card to the pile
     * @param card card
     */
    public void push(Card card) {
        cards.push(card);
        widget.addCard(boardWidgets.obtainCardWidget(card, null));
    }


    /**
     * get all cards
     * @return cards
     */
    public List<Card> getCards() {
        return List.copyOf(cards);
    }
}
