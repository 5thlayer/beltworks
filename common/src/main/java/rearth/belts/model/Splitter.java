package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Two belts in and two out, passing each item from an input's end to an output's head with
 * nothing held between (ADR-0076). Inputs and outputs are each taken in turn, so one input splits
 * evenly and two merge evenly; a side that cannot move is skipped, so a backed-up output sends
 * everything to the other. Each side passes no more than the splitter's own tier.
 */
public final class Splitter<T> {

    private final FlowLimit[] inputLimits;
    private final FlowLimit[] outputLimits;
    private int nextInput;
    private int nextOutput;

    public Splitter(double itemsPerTick) {
        inputLimits = new FlowLimit[] {new FlowLimit(itemsPerTick), new FlowLimit(itemsPerTick)};
        outputLimits = new FlowLimit[] {new FlowLimit(itemsPerTick), new FlowLimit(itemsPerTick)};
    }

    /**
     * Moves every item that can pass this tick. A missing side is null.
     *
     * @return whether any item passed
     */
    public boolean tick(long gameTime, @Nullable Lane<T> inLeft, @Nullable Lane<T> inRight,
                        @Nullable Lane<T> outLeft, @Nullable Lane<T> outRight) {
        var inputs = Arrays.asList(inLeft, inRight);
        var outputs = Arrays.asList(outLeft, outRight);
        var changed = false;
        while (passOne(gameTime, inputs, outputs)) changed = true;
        return changed;
    }

    private boolean passOne(long gameTime, List<Lane<T>> inputs, List<Lane<T>> outputs) {
        for (int i = 0; i < 2; i++) {
            var in = (nextInput + i) % 2;
            var input = inputs.get(in);
            if (input == null || !input.belt.endReady(input.length, input.speed)
                  || !inputLimits[in].ready(gameTime)) continue;
            for (int o = 0; o < 2; o++) {
                var out = (nextOutput + o) % 2;
                var output = outputs.get(out);
                if (output == null || !output.belt.canOffer(output.length, output.speed)
                      || !outputLimits[out].ready(gameTime)) continue;
                output.belt.offer(input.belt.takeEnd(), output.speed);
                inputLimits[in].pass();
                outputLimits[out].pass();
                nextInput = 1 - in;
                nextOutput = 1 - out;
                return true;
            }
        }
        return false;
    }

    /** A belt meeting the splitter, with the length and speed its own tick uses. */
    public record Lane<T>(BeltContents<T> belt, double length, double speed) {
    }
}
