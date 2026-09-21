package rearth.belts.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
