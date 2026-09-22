package rearth.belts.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;

/** A mid-belt support the belt item will place when the belt is created (PlanetaryFactory #366). */
public record PlannedSupport(BlockPos pos, Direction facing) {

    public static final Codec<PlannedSupport> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      BlockPos.CODEC.fieldOf("pos").forGetter(PlannedSupport::pos),
      Direction.CODEC.fieldOf("facing").forGetter(PlannedSupport::facing)
    ).apply(instance, PlannedSupport::new));

    public static final StreamCodec<ByteBuf, PlannedSupport> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, PlannedSupport::pos, Direction.STREAM_CODEC, PlannedSupport::facing, PlannedSupport::new);
}
