// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InserterSwingTest {

    // The wiki's "Cost per transfer cycle", committed in data/factorio/logistics.json.
    @ParameterizedTest
    @CsvSource({"2, 6650", "3, 8120", "4, 23200"})
    void aSwingIsTheWikisCycleCost(int tier, long joules) {
        assertEquals(joules, BeltTier.of(tier).inserter().joulesPerSwing());
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "2, 6650", "3, 8120", "4, 11600"})
    void anItemCostsASwingOverTheBaseHandSize(int tier, long joules) {
        assertEquals(joules, BeltTier.of(tier).loaderJoulesPerItem());
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "2, 400", "3, 500", "4, 1000"})
    void theDrainIsTheInsertersDrain(int tier, long watts) {
        assertEquals(watts, BeltTier.of(tier).loaderDrainWatts());
    }

    // Factorio's chest-to-chest cycles, in its ticks: the burner inserter's 0.6 items a second, the
    // inserter's 0.83, and the fast and bulk inserters' 2.31 a hand.
    @ParameterizedTest
    @CsvSource({"1, 100", "2, 72", "3, 26", "4, 26"})
    void aFeederReachingOneEachWaySwingsFactoriosCycle(int tier, int factorioTicks) {
        assertEquals(factorioTicks, 2 * BeltTier.of(tier).feederSwing().halfSwingTicks(FeederArms.ADJACENT));
    }

    // Stretching two blocks outlasts the half turn at every tier.
    @ParameterizedTest
    @CsvSource({"1, 94", "2, 58", "3, 20", "4, 20"})
    void anUnevenReachSwingsAsLongAsItsStretch(int tier, int factorioTicks) {
        var swing = BeltTier.of(tier).feederSwing();

        assertEquals(factorioTicks, swing.halfSwingTicks(FeederArms.ADJACENT.withTailReach(3)));
        assertEquals(factorioTicks, swing.halfSwingTicks(FeederArms.ADJACENT.withHeadReach(3)));
        assertTrue(factorioTicks > swing.halfSwingTicks(FeederArms.ADJACENT));
    }

    @ParameterizedTest
    @EnumSource(BeltTier.class)
    void reachingAlikeEachWayStretchesNothing(BeltTier tier) {
        var swing = tier.feederSwing();

        assertEquals(swing.halfSwingTicks(FeederArms.ADJACENT), swing.halfSwingTicks(new FeederArms(3, 3, FeederArms.Turn.STRAIGHT)));
    }

    @ParameterizedTest
    @CsvSource({"1, 25", "2, 18", "3, 7", "4, 7"})
    void aTurnedTailSwingsAQuarterTurn(int tier, int factorioTicks) {
        var swing = BeltTier.of(tier).feederSwing();

        assertEquals(factorioTicks, swing.halfSwingTicks(FeederArms.ADJACENT.withTailTurn(FeederArms.Turn.LEFT)));
        assertEquals(factorioTicks, swing.halfSwingTicks(FeederArms.ADJACENT.withTailTurn(FeederArms.Turn.RIGHT)));
    }

    @ParameterizedTest
    @CsvSource({"1, burner", "2, inserter", "3, inserter", "4, inserter"})
    void onlyTierOnesFeederSwingsTheBurnerInserter(int tier, String swing) {
        var of = BeltTier.of(tier);

        assertEquals(swing.equals("burner") ? InserterSwing.BURNER : of.inserter(), of.feederSwing());
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "2, 1", "3, 1", "4, 2"})
    void aFeedersHandHoldsItsInsertersHand(int tier, int items) {
        assertEquals(items, BeltTier.of(tier).feederSwing().handSize());
    }
}
