package org.coffinwood.gtklondike.game;

import org.coffinwood.gtklondike.ui.CardWidget;
import java.util.List;


/**
 * manage selections and click-based playing (1st click on source, 2nd click on target, double click to foundation)
 */
public class SelectionManager {
    private static Game game;
    private static List<Card> selectedRun;
    private static MoveSource selectedSource;


    /**
     * no constructor, make game logic available via this function; also called whenever the
     * board is rebuilt (new game / restart), so drop any selection referencing the discarded piles
     * @param game game logic
     */
    public static void init(Game game) {
        SelectionManager.game = game;
        selectedRun = null;
        selectedSource = null;
    }


    /**
     * a single click that landed on a card (null if empty pile area)
     * @param card card
     * @param sourcePile card's source pile
     * @param targetPile target pile
     */
    public static void handleClick(Card card, MoveSource sourcePile, MoveTarget targetPile) {
        if(selectedRun == null) {
            trySelect(card, sourcePile);
            return;
        }
        // clicking the pile that's already the selection source again cancels the selection
        if(sourcePile == selectedSource) {
            clearSelection();
            return;
        }
        if(targetPile != null && game.tryMove(selectedRun, selectedSource, targetPile)) {
            // no drag in flight here, so it's safe to detach the stale source widgets right away
            selectedSource.removeRunWidgets(selectedRun);
        }
        clearSelection();
    }


    /**
     * a double click on a card should move it to its corresponding foundation pile, given the pile accepts it
     * @param hitCard card to play
     * @param sourcePile source card pile
     */
    public static void handleDoubleClick(Card hitCard, MoveSource sourcePile) {
        if(hitCard == null || sourcePile == null) {
            return;
        }
        boolean wasAutoMoved = false;

        // top priority: auto-send to the first accepting foundation pile
        for(FoundationPile foundationPile : game.getFoundationPiles()) {
            List<Card> run = sourcePile.getRunStartingAt(hitCard);
            if(run != null && foundationPile.canAcceptRun(run) && game.tryMove(run, sourcePile, foundationPile)) {
                sourcePile.removeRunWidgets(run);
                wasAutoMoved = true;
                break;
            }
        }
        // low priority: if the double-clicked card couldn't be put on one of the foundation piles,
        // try with the tableau piles
        if(! wasAutoMoved) {
            for(TableauPile tableauPile : game.getTableauPiles()) {
                List<Card> run = sourcePile.getRunStartingAt(hitCard);
                if(run != null && tableauPile.canAcceptRun(run) && game.tryMove(run, sourcePile, tableauPile)) {
                    sourcePile.removeRunWidgets(run);
                    break;
                }
            }
        }

        // clear possible selection of a card
        clearSelection();
    }


    /**
     * clicks on the stock pile can only reveal / draw new cards
     */
    public static void handleClickOnStockPile() {
        clearSelection();
        game.drawCard();
    }


    /**
     * try to select a card (widget) with a click
     * @param card card
     * @param source card's source pile
     */
    private static void trySelect(Card card, MoveSource source) {
        if(card == null || source == null || !card.isFaceUp()) {
            return;
        }
        List<Card> run = source.getRunStartingAt(card);
        if(run == null) {
            return;
        }
        selectedRun = run;
        selectedSource = source;
        highlight(true);
    }


    /**
     * clear the current selection (via click, or a drag starting - see CardWidget.onDragBegin:
     * the press that initiates a drag also fires as an ordinary click on the ancestor PileWidget
     * before GTK recognizes it as a drag, so trySelect() runs and selects/highlights the card
     * being dragged; once the drag actually starts this needs to be undone, or that stale
     * selection silently eats the next real click)
     */
    public static void clearSelection() {
        highlight(false);
        selectedRun = null;
        selectedSource = null;
    }


    /**
     * highlight a card with a click
     * @param isSelected TRUE, if card shall be selected
     */
    private static void highlight(boolean isSelected) {
        if(selectedRun == null) {
            return;
        }
        for(Card card : selectedRun) {
            CardWidget cardWidget = selectedSource.getWidget().getWidget(card);
            if(cardWidget != null) {
                cardWidget.removeCssClass("card_selected");
                cardWidget.removeCssClass("card_normal");
                cardWidget.addCssClass(isSelected ? "card_selected" : "card_normal");
            }
        }
    }
}
