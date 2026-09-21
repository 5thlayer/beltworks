package rearth.belts.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import rearth.belts.Belts;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.client.BeltsClient;
import rearth.belts.client.renderers.BeltOutlineRenderer;

@Mod(value = Belts.MOD_ID, dist = Dist.CLIENT)
public final class BeltsModClientNeoForge {

    public BeltsModClientNeoForge(IEventBus eventBus) {
        BeltsClient.init();
        eventBus.addListener(this::registerRenderers);
        NeoForge.EVENT_BUS.addListener(this::extractOutline);
        NeoForge.EVENT_BUS.addListener(BeltHandClient::tick);
        NeoForge.EVENT_BUS.addListener(BeltHandClient::interact);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntitiesContent.CHUTE_BLOCK.get(), context -> new NeoForgeChuteBeltRenderer());
    }

    private void extractOutline(ExtractBlockOutlineRenderStateEvent event) {
        var outlines = BeltOutlineRenderer.extractPlannedBelt(event.getLevel(), event.getHitResult());
        if (outlines.isEmpty()) return;

        event.addCustomRenderer((renderState, buffers, poseStack, translucentPass, levelRenderState) -> {
            BeltOutlineRenderer.renderPlannedBelt(
                    outlines,
                    levelRenderState.cameraRenderState.pos,
                    poseStack,
                    buffers
            );
            return false;
        });
    }
}
