package rearth.belts.model;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import rearth.belts.model.SupportSlots.Click;
import rearth.belts.model.SupportSlots.OpenEnd;
import rearth.belts.model.SupportSlots.Refusal;
import rearth.belts.model.SupportSlots.Use;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The rows of #366's table: a support has one slot in and one out, and a click takes a free one. */
class SupportSlotsTest {

    private static final Use DEAD_END = new Use(true, false, false);
    private static final Use LOOSE_START = new Use(false, true, false);
    private static final Use JOIN = new Use(true, true, false);
    private static final Use MID_BELT = new Use(false, false, true);
    private static final Use FREE = new Use(false, false, false);

    @Test
    void aDeadEndTakesABeltStartingHere() {
        assertEquals(Optional.empty(), SupportSlots.refusal(DEAD_END, Click.START));
    }

    @Test
    void aDeadEndRefusesASecondBeltEndingHere() {
        assertEquals(Optional.of(Refusal.IN_TAKEN), SupportSlots.refusal(DEAD_END, Click.END));
    }

    @Test
    void aLooseStartRefusesASecondBeltStartingHere() {
        assertEquals(Optional.of(Refusal.OUT_TAKEN), SupportSlots.refusal(LOOSE_START, Click.START));
    }

    @Test
    void aLooseStartTakesABeltEndingHere() {
        assertEquals(Optional.empty(), SupportSlots.refusal(LOOSE_START, Click.END));
    }

    @Test
    void aJoinRefusesBothClicks() {
        assertEquals(Optional.of(Refusal.JOINED), SupportSlots.refusal(JOIN, Click.START));
        assertEquals(Optional.of(Refusal.JOINED), SupportSlots.refusal(JOIN, Click.END));
    }

    @Test
    void aMidBeltSupportRefusesBothClicks() {
        assertEquals(Optional.of(Refusal.MID_BELT), SupportSlots.refusal(MID_BELT, Click.START));
        assertEquals(Optional.of(Refusal.MID_BELT), SupportSlots.refusal(MID_BELT, Click.END));
    }

    @Test
    void aSupportNoBeltUsesTakesEither() {
        assertEquals(Optional.empty(), SupportSlots.refusal(FREE, Click.START));
        assertEquals(Optional.empty(), SupportSlots.refusal(FREE, Click.END));
    }

    @Test
    void aSneakClickOnAnExistingSupportIsAlwaysRefused() {
        for (var use : new Use[] {FREE, DEAD_END, LOOSE_START, JOIN, MID_BELT}) {
            assertEquals(Optional.of(Refusal.NOT_A_MIDPOINT), SupportSlots.refusal(use, Click.MIDPOINT), use.toString());
        }
    }

    @ParameterizedTest
    @EnumSource(Refusal.class)
    void eachRefusalHasItsOwnMessage(Refusal refusal) {
        assertEquals(Refusal.values().length,
          java.util.Arrays.stream(Refusal.values()).map(Refusal::messageKey).distinct().count());
        assertEquals("message.belts.support_" + refusal.name().toLowerCase(java.util.Locale.ROOT), refusal.messageKey());
    }

    @Test
    void anOpenEndIsALoaderAgainstAnInventoryAndASupportOtherwise() {
        assertEquals(OpenEnd.LOADER, OpenEnd.of(true));
        assertEquals(OpenEnd.SUPPORT, OpenEnd.of(false));
    }
}
