package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

/**
 * An entry handed from one belt's end to the next belt's head, at the sending belt's
 * {@link BeltContents#overshoot}, so it moves its speed across the join as along a belt
 * (PlanetaryFactory #366, #373). With no belt taking it, nothing passes and the sender backs up.
 *
 * <p>The sender hands on before it moves, since the overshoot looks a tick ahead; handed on after,
 * the entry would move twice in one tick. A receiver still to move this tick says so in its
 * {@link Splitter.Lane#pending}, and the entry is placed that far short.
 */
public final class Join {

    private Join() {
    }

    /**
     * Hands on the entry at the end of a belt that has not moved this tick.
     *
     * @param overshoot the sender's {@link BeltContents#overshoot}, read while the entry is its end
     * @return whether the receiver took it
     */
    public static <T> boolean offer(T payload, double overshoot, Splitter.@Nullable Lane<T> out) {
        if (out == null) return false;
        var at = overshoot - out.pending();
        if (!out.belt().canOffer(out.length(), at, out.hand())) return false;
        out.belt().offer(payload, out.length(), at, out.hand());
        return true;
    }

    /**
     * Hands on every entry that reaches the end of {@code in} this tick, before {@code in} moves.
     *
     * @return whether any item passed
     */
    public static <T> boolean pass(Splitter.Lane<T> in, Splitter.@Nullable Lane<T> out) {
        var passed = false;
        var held = in.hand() == null ? -1 : in.hand().point();
        while (out != null && in.belt().endReady(in.length(), in.speed(), held)) {
            var at = in.belt().overshoot(in.length(), in.speed()) - out.pending();
            if (!out.belt().canOffer(out.length(), at, out.hand())) break;
            out.belt().offer(in.belt().takeEnd(), out.length(), at, out.hand());
            passed = true;
        }
        return passed;
    }
}
