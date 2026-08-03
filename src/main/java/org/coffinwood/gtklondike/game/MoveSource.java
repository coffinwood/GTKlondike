package org.coffinwood.gtklondike.game;

import org.coffinwood.gtklondike.ui.PileWidget;
import java.util.List;


/**
 * Interface for an element that can be the source of a game move
 */
public interface MoveSource {
    List<Card> getRunStartingAt(Card card);
    // data-model removal only; must not touch widgets (see removeRunWidgets)
    void removeRun(List<Card> run);
    // detach the (already data-removed) run's now-stale widgets; callers decide when it's
    // safe to do so (immediately for click moves, deferred to drag-end for drag moves)
    void removeRunWidgets(List<Card> run);
    PileWidget getWidget();
}

