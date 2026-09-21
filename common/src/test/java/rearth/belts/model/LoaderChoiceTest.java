package rearth.belts.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoaderChoiceTest {

    @Test
    void eachEndTakesTheBeltsOwnTierFirst() {
        assertEquals(Optional.of(List.of(BeltTier.IMPROVED, BeltTier.IMPROVED)),
          LoaderChoice.of(BeltTier.IMPROVED, 2, held(Map.of(BeltTier.IMPROVED, 2, BeltTier.BELT, 2))));
    }

    @Test
    void anEndFallsBackToTheHighestLowerTierHeld() {
        assertEquals(Optional.of(List.of(BeltTier.EXPRESS, BeltTier.IMPROVED)),
          LoaderChoice.of(BeltTier.EXPRESS, 2, held(Map.of(BeltTier.EXPRESS, 1, BeltTier.IMPROVED, 1, BeltTier.BELT, 3))));
    }

    @Test
    void aHigherTierIsNeverTaken() {
        assertEquals(Optional.empty(), LoaderChoice.of(BeltTier.BELT, 1, held(Map.of(BeltTier.TURBO, 9))));
    }

    @Test
    void tooFewLoadersIsARefusal() {
        assertEquals(Optional.empty(), LoaderChoice.of(BeltTier.BELT, 2, held(Map.of(BeltTier.BELT, 1))));
        assertEquals(Optional.empty(), LoaderChoice.of(BeltTier.TURBO, 1, held(Map.of())));
    }

    @Test
    void noMissingEndNeedsNothing() {
        assertEquals(Optional.of(List.of()), LoaderChoice.of(BeltTier.BELT, 0, held(Map.of())));
    }

    private static EnumMap<BeltTier, Integer> held(Map<BeltTier, Integer> counts) {
        var map = new EnumMap<BeltTier, Integer>(BeltTier.class);
        map.putAll(counts);
        return map;
    }
}
