package org.coffinwood.gtklondike.game;


/**
 * immutable snapshot of a single {@link Card}'s identity and face-up state, used to record/restore
 * board states for undo without holding onto (and risking later mutation of) the live Card objects
 * @param isBlack suit colour
 * @param suit suit
 * @param rank rank
 * @param isFaceUp face-up state at the time of the snapshot
 */
record CardState(boolean isBlack, int suit, int rank, boolean isFaceUp) {


    /**
     * capture a card's current state
     * @param card card to capture
     * @return snapshot of the card
     */
    static CardState of(Card card) {
        return new CardState(card.isBlack(), card.getSuit(), card.getRank(), card.isFaceUp());
    }


    /**
     * build a fresh, independent Card matching this snapshot
     * @return new card instance
     */
    Card toCard() {
        Card card = new Card(isBlack, suit, rank);
        card.setFaceUp(isFaceUp);
        return card;
    }
}
