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
    // much smaller than the trailing-batch fan offset passed into the constructor - shifts the
    // visible front card just enough that an older/covered card peeks out from behind it whenever
    // the pile holds more than one card, even in draw-1 mode where the trailing-batch fan alone
    // never shows anything (every batch is a single card). See PileLayoutManager.setFrontOffsetX().
    // Kept small so the cue stays a subtle hint rather than an eye-catching jump each time a card
    // is drawn/played and the peek appears/disappears - there's no animation smoothing that
    // transition (yet), so a stronger offset would read as more of a "twitch". A translucent peek
    // (opacity on the peeking card) was tried as a further softener but caused GTK compositing
    // flicker, so this offset reduction alone is the mitigation for now
    private static final double FRONT_OFFSET_X = 5.0;

    private final List<Card> cards = new ArrayList<>();
    private final PileWidget widget;
    private final PileLayoutManager layoutManager;
    private final BoardWidgets boardWidgets;
    // how many of the pile's trailing cards still belong to the most recent draw batch and should
    // stay fanned out - shrinks as cards are played off the top (see removeRun()), independently
    // of the pile's total card count. PileLayoutManager.setFanCount() takes a *count*, not a fixed
    // set of cards, so without tracking this separately, once this batch shrinks below its
    // original size, the fan's trailing window (childCount - fanCount) would slide into whatever
    // older, already-covered batch sits behind it and incorrectly re-reveal one of its cards
    private int fanRemaining = 0;


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
        layoutManager.setFrontOffsetX(FRONT_OFFSET_X);
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
        fanRemaining = cards.size();
        layoutManager.setFanCount(fanRemaining);
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
        // shrink the fan along with whatever's left of the current draw batch - see fanRemaining
        if(fanRemaining > 0) {
            fanRemaining--;
            layoutManager.setFanCount(fanRemaining);
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
        fanRemaining = 0;
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
     * number of cards currently on the pile
     * @return card count
     */
    public int size() {
        return cards.size();
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
