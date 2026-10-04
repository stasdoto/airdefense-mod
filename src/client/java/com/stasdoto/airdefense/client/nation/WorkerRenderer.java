package com.stasdoto.airdefense.client.nation;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

import com.stasdoto.airdefense.nation.WorkerEntity;

/** Villagers at work: the same person as before (see {@link SkinBaker}) in the gear of the job, tool in hand. */
public class WorkerRenderer extends HumanoidMobRenderer<WorkerEntity, AvatarRenderState, PlayerModel> {
	public WorkerRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(WorkerEntity w, AvatarRenderState s, float partialTick) {
		super.extractRenderState(w, s, partialTick);
		String[] outfit = w.outfit().split(";", 2);
		Identifier skin = SkinBaker.worker(w.look(), w.job(), outfit[0], outfit.length > 1 ? outfit[1] : "plains");
		s.setData(SoldierRenderer.SKIN, skin);
		s.skin = SkinBaker.skin(skin);
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState s) {
		Identifier id = s.getData(SoldierRenderer.SKIN);
		return id != null ? id : SkinBaker.villager(0, "none", "plains");
	}
}
