package org.coffinwood.gtklondike.game;

import java.util.List;


/**
 * immutable snapshot of an entire {@link Game}'s board, used as one step of undo history. Every
 * card list is bottom-to-top order (i.e. the order the pile's own push()/deal() would rebuild it
 * in), except {@code stockPile} which is front-to-back "next card to be drawn" order, matching
 * {@link StockPile}'s own constructor/getCards() convention.
 * @param tableauPiles 7 tableau lanes, bottom-to-top
 * @param foundationPiles 4 foundations (index == suit), bottom-to-top
 * @param stockPile stock, next-to-draw first
 * @param wastePile waste, first-drawn (bottom) to most-recently-drawn (top)
 * @param drawAmount draw amount (1 or 3) in effect when this snapshot was taken
 */
record GameSnapshot(List<List<CardState>> tableauPiles, List<List<CardState>> foundationPiles,
                     List<CardState> stockPile, List<CardState> wastePile, int drawAmount) {}
