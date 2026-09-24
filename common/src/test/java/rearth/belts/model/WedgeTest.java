package rearth.belts.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What stands under a slope: a wedge over air, nothing over ground, a refusal over anything else (PlanetaryFactory #420). */
class WedgeTest {

    @Test
    void aMiddleOrTopOverAirGetsAWedge() {
        for (var pitch : new Pitch[] {Pitch.MIDDLE_UP, Pitch.TOP_UP, Pitch.MIDDLE_DOWN, Pitch.TOP_DOWN}) {
            assertEquals(Wedge.Verdict.PLACE, Wedge.under(pitch, Wedge.Below.REPLACEABLE), pitch.name());
        }
    }

    @Test
    void aLevelTileOrAFootNeedsNone() {
        for (var pitch : new Pitch[] {Pitch.LEVEL, Pitch.FOOT_UP, Pitch.FOOT_DOWN}) {
            for (var below : Wedge.Below.values()) {
                assertEquals(Wedge.Verdict.NONE, Wedge.under(pitch, below), pitch.name() + " over " + below.name());
            }
        }
    }

    @Test
    void aSlopeOverSolidGroundNeedsNone() {
        assertEquals(Wedge.Verdict.NONE, Wedge.under(Pitch.TOP_UP, Wedge.Below.SOLID));
        assertEquals(Wedge.Verdict.NONE, Wedge.under(Pitch.MIDDLE_DOWN, Wedge.Below.SOLID));
    }

    @Test
    void aSlopeOverATileLoaderMachineOrFluidIsRefused() {
        assertEquals(Wedge.Verdict.REFUSED, Wedge.under(Pitch.TOP_UP, Wedge.Below.OCCUPIED));
        assertEquals(Wedge.Verdict.REFUSED, Wedge.under(Pitch.MIDDLE_DOWN, Wedge.Below.OCCUPIED));
    }

    // The wedge rises the way the slope over it does, so a descent's wedge faces back along its travel.
    @Test
    void aWedgeRisesTheWayItsSlopeDoes() {
        var east = new LineScan.Travel(1, 0);
        var west = new LineScan.Travel(-1, 0);
        assertEquals(east, Wedge.uphill(Pitch.MIDDLE_UP, east));
        assertEquals(east, Wedge.uphill(Pitch.TOP_UP, east));
        assertEquals(west, Wedge.uphill(Pitch.MIDDLE_DOWN, east));
        assertEquals(west, Wedge.uphill(Pitch.TOP_DOWN, east));
    }
}
