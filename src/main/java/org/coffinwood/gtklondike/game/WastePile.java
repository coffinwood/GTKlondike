package org.coffinwood.gtklondike.game;

import org.coffinwood.gtklondike.ui.CardWidget;
import org.coffinwood.gtklondike.ui.PileLayoutManager;
import org.coffinwood.gtklondike.ui.PileWidget;
import java.util.ArrayList;
import java.util.List;


/**
 * waste pile with 1 or 3 face-up cards
 */
public class WastePile implements MoveSource {
    private final List<Card> cards = new ArrayList<>();
    private final PileWidget widget;
    private final PileLayoutManager layoutManager;
    private final BoardWidgets boardWidgets;


    /**
     * constructor
     * @param offsetX horizontal pixel offset the trailing draw batch is fanned out by; has no
     *   visible effect for a batch of 1 card (draw-1 mode), see pushBatch()
     * @param boardWidgets the board's persistent/pooled widgets
     */
    public WastePile(int offsetX, BoardWidgets boardWidgets) {
        this.boardWidgets = boardWidgets;
        widget = boardWidgets.getWasteWidget();
        layoutManager = widget.getPileLayoutManager();
        // flush stacking vertically (like stock/foundation) - only the horizontal fan cascades
        layoutManager.setCardOffsetY(0);
        layoutManager.setCardOffsetX(offsetX);
        widget.setWidgetOwner(this);
    }


    /**
     * return widget
     * @return card pile's widget
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
        widget.addCard(boardWidgets.obtainCardWidget(card, this));
    }


    /**
     * add a batch of cards drawn together (e.g. the up-to-3 cards of one draw-3 pull) and mark
     * that whole batch as the current horizontal fan, so it's shown spread out while everything
     * drawn earlier stays flush/covered behind it. Playing cards off the top shrinks the fan
     * automatically (PileLayoutManager clamps the fan to however many cards actually remain), so
     * there's nothing to update here as the batch gets played down.
     * @param cards cards drawn together, in draw order
     */
    public void pushBatch(List<Card> cards) {
        for(Card card : cards) {
            push(card);
        }
        layoutManager.setFanCount(cards.size());
    }


    /**
     * look at top card
     * @return top card
     */
    public Card peek() {
        return cards.isEmpty() ? null : cards.getLast();
    }


    /**
     * return run of cards (actually 1 card) from the pile
     * @param startCard card the run shall start at
     * @return run of cards
     */
    @Override
    public List<Card> getRunStartingAt(Card startCard) {
        Card card = peek();
        if(card == null) {
            return null;
        }
        return List.of(card);
    }


    /**
     * remove a run on the waste pile always means remove the top card (data model only, widget stays parented)
     * @param run run of cards to remove
     */
    @Override
    public void removeRun(List<Card> run) {
        cards.removeLast();
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
     * empty the pile
     * @return all cards in pile
     */
    public List<Card> drainAll() {
        List<Card> drained = new ArrayList<>(cards);
        cards.clear();
        for(CardWidget cardWidget : new ArrayList<>(widget.getCardWidgets())) {
            widget.removeCard(cardWidget);
            boardWidgets.releaseCardWidget(cardWidget);
        }
        layoutManager.setFanCount(0);
        return drained;
    }


    /**
     * is pile empty?
     * @return TRUE, if pile is empty
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }


    /**
     * all cards currently on the pile, first-drawn (bottom) to most-recently-drawn (top) - the
     * same order repeated push() calls would rebuild the pile in
     * @return cards, bottom to top
     */
    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }
}
