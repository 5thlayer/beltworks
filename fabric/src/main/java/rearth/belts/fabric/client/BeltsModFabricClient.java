package rearth.belts.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.BlockHitResult;
import rearth.belts.client.BeltsClient;
import rearth.belts.client.renderers.BeltOutlineRenderer;

import java.util.List;

public final class BeltsModFabricClient implements ClientModInitializer {

    private static volatile List<BeltOutlineRenderer.Outline> plannedBelt = List.of();

    @Override
    public void onInitializeClient() {
        BeltsClient.init();
        BeltsClient.registerRenderers();

        LevelRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hitResult) -> {
            plannedBelt = hitResult instanceof BlockHitResult blockHit
                    ? BeltOutlineRenderer.extractPlannedBelt(context.level(), blockHit)
                    : List.of();
        });
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outlineState) -> {
            BeltOutlineRenderer.renderPlannedBelt(
                    plannedBelt,
                    context.levelState().cameraRenderState.pos,
                    context.poseStack(),
                    context.bufferSource()
            );
            return true;
        });
    }
}
