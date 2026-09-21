package rearth.belts.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeltCostTest {

    @ParameterizedTest
    @CsvSource({"1, 1", "4, 4", "64, 64", "0.3, 1", "3.1, 4", "7.2, 8"})
    void aBeltCostsOneItemPerBlockRoundedUp(double length, int items) {
        assertEquals(items, BeltCost.of(length));
    }

    // A spline's length carries float error, and a straight 64-block belt must not cost 65.
    @ParameterizedTest
    @CsvSource({"64.0000001, 64", "63.9999999, 64", "8.04, 8"})
    void theLengthIsMeasuredInWholeSlotsFirst(double length, int items) {
        assertEquals(items, BeltCost.of(length));
    }

    @ParameterizedTest
    @CsvSource({"64.0000001, 512", "3.1, 25"})
    void theBeltHoldsWhatItsSlotsHold(double length, long slots) {
        assertEquals(slots, BeltCost.slots(length));
    }
}
