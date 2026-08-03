package org.coffinwood.gtklondike.game;

import java.util.List;


/**
 * a payload of cards that gets dragged across the UI
 *
 * @param sourcePile wherever they came from, for cancel/rollback
 */
public record CardRunPayload(List<Card> cards, MoveSource sourcePile) {

    /**
     * constructor
     * @param cards      run of cards (or 1)
     * @param sourcePile card pile it's from
     */
    public CardRunPayload {
    }
}
