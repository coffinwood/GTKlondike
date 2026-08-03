package org.coffinwood.gtklondike.game;

import org.coffinwood.gtklondike.ui.PileWidget;
import java.util.List;


/**
 * interface of an element that can be the target for a game move
 */
public interface MoveTarget {
    boolean canAcceptRun(List<Card> run);
    void addRun(List<Card> run);
    PileWidget getWidget();
}
