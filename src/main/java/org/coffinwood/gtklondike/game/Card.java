package org.coffinwood.gtklondike.game;


/**
 * datatype for a card
 */
public class Card {
    int suit, rank;
    boolean isBlack, isFaceUp;


    /**
     * Card
     * @param isBlack TRUE, if suit is Club or Spade
     * @param suit Club, Spade, Heart, or Diamond
     * @param rank 0 = Ace, 2..12 (10 = Jack, 11 = Queen, 12 = King)
     */
    public Card(boolean isBlack, int suit, int rank) {
        this.isBlack = isBlack;
        this.suit = suit;
        this.rank = rank;
        isFaceUp = false;
    }


    /**
     * return "binary" colour (black/not black)
     * @return colour
     */
    public boolean isBlack() {
        return isBlack;
    }


    /**
     * return suit
     * @return suit 0..4
     */
    public int getSuit() {
        return suit;
    }


    /**
     * return rank
     * @return rank 0..12
     */
    public int getRank() {
        return rank;
    }


    /**
     * is this card turned up?
     * @return TRUE, if face is up
     */
    public boolean isFaceUp() {
        return isFaceUp;
    }


    /**
     * set the card's status (face up / down)
     * @param isFaceUp up / down
     */
    public void setFaceUp(boolean isFaceUp) {
        this.isFaceUp = isFaceUp;
    }


    /**
     * generate String for debugging
     * @return String representation
     */
    public String toString() {
        return "card " + suit + "_" + rank + " (" + isBlack + ")";
    }
}
