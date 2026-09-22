package rearth.belts.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import rearth.belts.Belts;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.client.BeltsClient;
import rearth.belts.client.renderers.BeltPreview;

@Mod(value = Belts.MOD_ID, dist = Dist.CLIENT)
public final class BeltsModClientNeoForge {

    public BeltsModClientNeoForge(IEventBus eventBus) {
        BeltsClient.init();
        eventBus.addListener(this::registerRenderers);
        NeoForge.EVENT_BUS.addListener(this::extractOutline);
        NeoForge.EVENT_BUS.addListener(this::submitPreview);
        NeoForge.EVENT_BUS.addListener(BeltHandClient::tick);
        NeoForge.EVENT_BUS.addListener(BeltHandClient::interact);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntitiesContent.CHUTE_BLOCK.get(), context -> new NeoForgeChuteBeltRenderer());
    }

    private void extractOutline(ExtractBlockOutlineRenderStateEvent event) {
        var plan = BeltPreview.currentPlan();
        if (plan == null) return;
        var outlines = BeltPreview.outlines(plan, event.getLevel().getGameTime());
        if (outlines.isEmpty()) return;

        event.addCustomRenderer((renderState, buffers, poseStack, translucentPass, levelRenderState) -> {
            BeltPreview.renderOutlines(outlines, levelRenderState.cameraRenderState.pos, poseStack, buffers);
            return false;
        });
    }

    private void submitPreview(SubmitCustomGeometryEvent event) {
        var plan = BeltPreview.currentPlan();
        BeltPreview.announce(plan);
        if (plan == null) return;
        BeltPreview.submit(plan, event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState.pos);
    }
}
