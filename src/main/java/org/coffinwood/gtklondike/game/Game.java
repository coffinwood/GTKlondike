package org.coffinwood.gtklondike.game;

import java.util.*;


/**
 * TODO put offset values for card piles into options
 */
public class Game {
    // how many past board states undo can step back through
    private static final int MAX_UNDO_STEPS = 20;

    private StockPile stockPile;
    private WastePile wastePile;
    private final List<TableauPile> tableauPiles = new ArrayList<>(7);
    private final List<FoundationPile> foundationPiles = new ArrayList<>(4);
    // the original shuffle order for this game, captured before deal() consumes a copy of it;
    // lets restart() redeal the exact same layout instead of a fresh shuffle
    private final List<Card> initialDeckOrder;
    private int drawAmount;
    private boolean hasStarted = false;
    private boolean isVictory = false;
    public Runnable onVictory; // set once by GTKlondike
    // board states captured just before each undoable mutation, most recent last
    private final Deque<GameSnapshot> undoStack = new ArrayDeque<>();
    // notified whenever canUndo()/canAutoComplete() may have changed, so the UI can enable/
    // disable its Undo/Auto-Complete controls
    public Runnable onGameStateChanged;
    // the board's persistent/pooled pile and card widgets - see BoardWidgets
    private final BoardWidgets boardWidgets;

    /**
     * deals a freshly shuffled deck
     * @param drawAmount number of cards drawn from the stock at a time
     * @param boardWidgets the board's persistent/pooled widgets
     */
    public Game(int drawAmount, BoardWidgets boardWidgets) {
        this.drawAmount = drawAmount;
        this.boardWidgets = boardWidgets;

        List<Card> deck = buildShuffledDeck();
        this.initialDeckOrder = cloneDeck(deck);
        deal(deck);
    }


    /**
     * deal a specific, already-known deck order (e.g. one drawn from SolvedDeals) instead of a
     * fresh shuffle
     * @param drawAmount number of cards drawn from the stock at a time
     * @param boardWidgets the board's persistent/pooled widgets
     * @param fixedDeckOrder the deck order to deal, bottom-to-top as consumed by deal(); not
     *                       mutated or retained by the caller afterwards
     */
    public Game(int drawAmount, BoardWidgets boardWidgets, List<Card> fixedDeckOrder) {
        this.drawAmount = drawAmount;
        this.boardWidgets = boardWidgets;

        this.initialDeckOrder = cloneDeck(fixedDeckOrder);
        deal(cloneDeck(fixedDeckOrder));
    }


    /**
     * redeal the exact same shuffle this game started with, e.g. to retry a misplayed hand
     */
    public void restart() {
        deal(cloneDeck(initialDeckOrder));
        undoStack.clear();
        notifyGameStateChanged();
    }


    /**
     * build one shuffled 52-card deck
     * @return shuffled deck
     */
    private List<Card> buildShuffledDeck() {
        // make a temporary ArrayList that can be shuffled, because ArrayDeque can't
        ArrayList<Card> deck = new ArrayList<>();
        for(int suit = 0; suit < 4; suit++) {
            for(int rank = 0; rank < 13; rank++) {
                deck.add(new Card(suit < 2, suit, rank));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }


    /**
     * deep-clone a deck (fresh Card instances) so later mutations (e.g. isFaceUp during play)
     * don't corrupt a stored/archived order
     * @param deck deck to clone
     * @return cloned deck
     */
    private static List<Card> cloneDeck(List<Card> deck) {
        List<Card> clone = new ArrayList<>(deck.size());
        for(Card card : deck) {
            clone.add(new Card(card.isBlack(), card.getSuit(), card.getRank()));
        }
        return clone;
    }


    /**
     * deal a deck into fresh piles, replacing whatever piles this Game currently has
     * @param deck deck to deal; consumed (removeLast) from the end
     */
    private void deal(List<Card> deck) {
        // restart() reuses this Game object rather than going through the constructor, so this is
        // the one place both the constructor and restart() run through that can reset the flag
        isVictory = false;
        // whatever's left over from however the previous game ended is scattered across all 13
        // persistent pile widgets, not just the ones a normal move/draw would have touched - has
        // to run before anything below starts pushing this deal's cards into those same widgets
        boardWidgets.reclaimAllCardWidgets();
        tableauPiles.clear();
        foundationPiles.clear();

        // fill lanes with cards corresponding with their number
        for(int laneIndex = 0; laneIndex < 7; laneIndex++) {
            TableauPile lanePile = new TableauPile(28, laneIndex, boardWidgets);

            for(int cardIndex = 0; cardIndex < laneIndex; cardIndex++) {
                Card card = deck.removeLast();
                card.setFaceUp(false);
                lanePile.push(card);
            }
            // ...one visible card on top
            Card faceCard = deck.removeLast();
            faceCard.setFaceUp(true);
            lanePile.push(faceCard);

            tableauPiles.add(lanePile);
        }

        // remaining cards make up the stock pile
        stockPile = new StockPile(new ArrayList<>(deck), boardWidgets);
        // flush stacking (like stock/foundation), not a tableau-style cascade: the waste pile
        // keeps every drawn-but-unplayed card (only the top one matters), and PileLayoutManager's
        // measure() grows the pile's requested height by cardOffsetY per card, so a nonzero offset
        // here made the pile demand ever more vertical space as more cards were drawn
        wastePile = new WastePile(28, boardWidgets);

        for(int suit = 0; suit < 4; suit++) {
            foundationPiles.add(new FoundationPile(suit, 0, boardWidgets));
        }
    }


    /**
     * Is the stock pile itself out of cards? TRUE both once there's nothing left to draw or
     * recycle at all.
     * @return TRUE if the stock pile has no cards left
     */
    public boolean isStockPileEmpty() {
        return stockPile.isEmpty();
    }


    /**
     * number of cards currently on the waste pile
     * @return card count
     */
    public int getWasteCardCount() {
        return wastePile.size();
    }


    /**
     * number of cards drawn from the stock at a time
     * @return draw amount (1 or 3)
     */
    public int getDrawAmount() {
        return drawAmount;
    }


    /**
     * return list of tableau piles
     * @return list of tableau card piles
     */
    public List<TableauPile> getTableauPiles() {
        return tableauPiles;
    }


    /**
     * return list of foundation piles
     * @return list of foundation card piles
     */
    public List<FoundationPile> getFoundationPiles() {
        return foundationPiles;
    }


    /**
     * (try to) draw a card from the hidden cards deck
     */
    public void drawCard() {
        // return if all piles are empty
        if(stockPile.isEmpty() && wastePile.isEmpty()) {
            return;
        }
        // one snapshot per user-initiated draw, even though performDraw() may recurse once
        // (stock empty -> recycle from waste -> try again) to satisfy the same draw request
        pushUndoSnapshot();
        performDraw();
        // notified after the draw (and any recycle) actually happened, not before
        notifyGameStateChanged();
    }


    /**
     * the actual (possibly self-recursing) draw logic, split out from drawCard() so recycling the
     * waste pile back into the stock and retrying doesn't push a second undo snapshot
     */
    private void performDraw() {
        performDraw(false);
    }


    /**
     * @param isMeaninglessCycle TRUE if this draw is a recycle-and-redraw that's just going to
     *                           reshuffle the exact same up-to-drawAmount cards straight back into
     *                           view (see the recycle branch below) - doesn't progress the game,
     *                           so it shouldn't bump the move-count stat
     */
    private void performDraw(boolean isMeaninglessCycle) {
        // return if all piles are empty
        if(stockPile.isEmpty() && wastePile.isEmpty()) {
            return;
        }

        // if the stock pile is empty, put all cards from the waste pile back into the stock pile and try again
        if (stockPile.isEmpty()) {
            // the waste pile holds every card this recycle is about to redeal - if that's no more
            // than one batch's worth, recycling+redrawing hands back the identical set of cards in
            // the identical order, so it's not a real move (see GTKlondike.updateStockPileEmptyState(),
            // which shows a "0" on the stock pile for this same situation)
            boolean nothingNewToDraw = wastePile.size() <= drawAmount;
            List<Card> reclaimed = wastePile.drainAll();
            Collections.reverse(reclaimed);
            for(Card card : reclaimed) {
                card.setFaceUp(false);
                stockPile.push(card);
            }
            performDraw(nothingNewToDraw);
            return;
        }

        // draw cards until the amount of drawn cards is reached (usually 1 or 3), then push them
        // onto the waste pile as one batch so it can fan them out as this draw's group
        List<Card> drawnBatch = new ArrayList<>();
        for(int drawnCards = 0; drawnCards < drawAmount && !stockPile.isEmpty(); drawnCards++) {
            Card card = stockPile.draw();
            card.setFaceUp(true);
            drawnBatch.add(card);
        }
        wastePile.pushBatch(drawnBatch);
        if(! isMeaninglessCycle) {
            // count draw as a game move *and* as a flag that a game was really started
            addStats();
        }
    }


    /**
     * try a move
     * @param run run of cards to move (may be 1 card only)
     * @param sourcePile from pile
     * @param target to pile
     * @return TRUE, if move is acceptable
     */
    public boolean tryMove(List<Card> run, MoveSource sourcePile, MoveTarget target) {
        if(run == null || run.isEmpty() || sourcePile == null || target == null) {
            return false;
        }

        // the logic to test if a run can be accepted sit inside the piles
        if(! target.canAcceptRun(run)) {
            return false;
        }
        // "remember" game board for an eventual undo
        pushUndoSnapshot();
        // remove card(s) from source
        sourcePile.removeRun(run);
        // add card(s) to target
        target.addRun(run);
        // count as a played game (once) and count as a game move
        addStats();

        checkForVictory();
        // fired after the mutation (and the victory check) so a listener re-checking
        // canAutoComplete()/canUndo() sees this move's actual effect, not the state before it
        notifyGameStateChanged();
        return true;
    }


    /**
     * is every remaining card already exposed, so the rest of the game is just clicking each
     * tableau top card over to its foundation one by one? Once the stock/waste are empty and
     * nothing is left face-down, no further decisions matter - autoComplete() can finish the
     * game on its own.
     * @return TRUE if the tableau piles hold nothing but face-up cards and stock/waste are empty
     */
    public boolean canAutoComplete() {
        if(! stockPile.isEmpty() || ! wastePile.isEmpty()) {
            return false;
        }
        for(TableauPile pile : tableauPiles) {
            for(Card card : pile.getCards()) {
                if(! card.isFaceUp()) {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * play exactly one card of an already-decided game (see canAutoComplete()) over to its
     * foundation, then return - it's the caller's job to call this repeatedly (e.g. from a paced
     * timer, so the player can actually watch it happen) until it returns FALSE.
     */
    public boolean autoCompleteStep() {
        for(TableauPile tableauPile : tableauPiles) {
            Card top = tableauPile.peek();
            if(top == null) {
                continue;
            }
            FoundationPile foundation = foundationPiles.get(top.getSuit());
            List<Card> run = tableauPile.getRunStartingAt(top);
            if(run != null && foundation.canAcceptRun(run) && tryMove(run, tableauPile, foundation)) {
                // no drag in flight during auto-complete, so it's safe to detach right away
                // (same reasoning SelectionManager's click-based moves already rely on)
                tableauPile.removeRunWidgets(run);
                return true;
            }
        }
        return false;
    }


    /**
     * check if game is won: all foundation piles are full
     */
    private void checkForVictory() {
        // save old state to ensure that the victory is only celebrated once
        boolean wasVictory = isVictory;
        isVictory = getFoundationPiles().stream().allMatch(FoundationPile::isFull);
        if(isVictory && !wasVictory && onVictory != null) {
            Statistics.addGameWon();
            SolvedDeals.recordSolvedDeal(drawAmount, initialDeckOrder);
            onVictory.run();
        }
    }


    /**
     * has the current game already been won?
     * @return TRUE if all foundation piles are full
     */
    public boolean isVictory() {
        return isVictory;
    }


    /**
     * has any move or draw happened on this deal yet? FALSE right after a fresh deal (New Game/
     * Restart/Deal a Solvable Game) until the player's first draw or move - e.g. so switching the
     * draw amount before then can redeal silently instead of asking for confirmation, since
     * nothing would actually be lost
     * @return TRUE if addStats() has counted at least one move on this deal
     */
    public boolean hasStarted() {
        return hasStarted;
    }


    /**
     * is there a previous board state to undo to?
     * @return TRUE if undo() would do something
     */
    public boolean canUndo() {
        return ! undoStack.isEmpty();
    }


    /**
     * step back to the board state just before the last undoable move/draw. Rebuilds every pile
     * from scratch (same approach restart()/deal() already use) rather than trying to hand-write
     * an inverse of each mutation, since tableau/foundation auto-flip and stock-recycling face
     * transitions would otherwise each need their own careful reversal.
     * @return TRUE, if a previous state existed and was restored
     */
    public boolean undo() {
        if(undoStack.isEmpty()) {
            return false;
        }
        restoreSnapshot(undoStack.removeLast());
        notifyGameStateChanged();
        return true;
    }


    /**
     * Let the UI know canUndo()/canAutoComplete() may have changed, if it's listening.
     */
    private void notifyGameStateChanged() {
        if(onGameStateChanged != null) {
            onGameStateChanged.run();
        }
    }


    /**
     * capture the current board and push it onto the undo stack, dropping the oldest entry once
     * MAX_UNDO_STEPS is exceeded
     */
    private void pushUndoSnapshot() {
        undoStack.addLast(captureSnapshot());
        while(undoStack.size() > MAX_UNDO_STEPS) {
            undoStack.removeFirst();
        }
    }


    /**
     * capture every pile's current contents (and face-up state) as an immutable snapshot
     * @return snapshot of the current board
     */
    private GameSnapshot captureSnapshot() {
        List<List<CardState>> tableauSnapshot = new ArrayList<>();
        for(TableauPile pile : tableauPiles) {
            tableauSnapshot.add(toCardStates(pile.getCards()));
        }

        List<List<CardState>> foundationSnapshot = new ArrayList<>();
        for(FoundationPile pile : foundationPiles) {
            foundationSnapshot.add(toCardStates(pile.getCards()));
        }

        return new GameSnapshot(
                tableauSnapshot,
                foundationSnapshot,
                toCardStates(stockPile.getCards()),
                toCardStates(wastePile.getCards()),
                drawAmount);
    }


    /**
     * rebuild every pile from a previously captured snapshot, replacing the game's current piles
     * entirely - mirrors how deal() builds piles from scratch, just fed snapshot data instead of a
     * freshly shuffled deck
     * @param snapshot snapshot to restore
     */
    private void restoreSnapshot(GameSnapshot snapshot) {
        // see deal()'s identical call - undo replaces every pile's cards the same way a fresh
        // deal does, so it needs the same sweep of whatever these persistent widgets still hold
        boardWidgets.reclaimAllCardWidgets();

        tableauPiles.clear();
        for(int laneIndex = 0; laneIndex < snapshot.tableauPiles().size(); laneIndex++) {
            TableauPile lane = new TableauPile(28, laneIndex, boardWidgets);
            for(CardState state : snapshot.tableauPiles().get(laneIndex)) {
                lane.push(state.toCard());
            }
            tableauPiles.add(lane);
        }

        foundationPiles.clear();
        for(int suit = 0; suit < snapshot.foundationPiles().size(); suit++) {
            FoundationPile foundation = new FoundationPile(suit, 0, boardWidgets);
            for(CardState state : snapshot.foundationPiles().get(suit)) {
                foundation.push(state.toCard());
            }
            foundationPiles.add(foundation);
        }

        List<Card> stockCards = new ArrayList<>();
        for(CardState state : snapshot.stockPile()) {
            stockCards.add(state.toCard());
        }
        stockPile = new StockPile(new ArrayList<>(stockCards), boardWidgets);

        wastePile = new WastePile(28, boardWidgets);
        for(CardState state : snapshot.wastePile()) {
            wastePile.push(state.toCard());
        }

        drawAmount = snapshot.drawAmount();
        // recompute silently: undoing should never (re-)trigger the victory popup
        isVictory = getFoundationPiles().stream().allMatch(FoundationPile::isFull);
    }


    /**
     * add to statistics that a game move was made and - only once - that a game was started by moving cards
     */
    private void addStats() {
        Statistics.addMove();
        if(! hasStarted) {
            hasStarted  = true;
            Statistics.addGamePlayed();
        }
    }


    /**
     * snapshot every card in a pile's current (already correctly ordered) card list
     * @param cards cards, in whatever order the pile itself considers bottom-to-top/draw order
     * @return immutable per-card snapshots in the same order
     */
    private static List<CardState> toCardStates(List<Card> cards) {
        List<CardState> states = new ArrayList<>(cards.size());
        for(Card card : cards) {
            states.add(CardState.of(card));
        }
        return states;
    }
}
