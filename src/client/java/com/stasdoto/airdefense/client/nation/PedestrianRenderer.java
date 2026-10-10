package com.stasdoto.airdefense.client.nation;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

import com.stasdoto.airdefense.nation.PedestrianEntity;

/** 1.37: passers-by drawn like the towns' other people (see {@link SkinBaker}), children smaller. */
public class PedestrianRenderer extends HumanoidMobRenderer<PedestrianEntity, AvatarRenderState, PlayerModel> {
	public PedestrianRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(PedestrianEntity p, AvatarRenderState s, float partialTick) {
		super.extractRenderState(p, s, partialTick);
		String[] outfit = p.outfit.split(";", 2);
		Identifier skin = SkinBaker.villager(p.look, outfit[0], outfit.length > 1 ? outfit[1] : "plains");
		s.setData(SoldierRenderer.SKIN, skin);
		s.skin = SkinBaker.skin(skin);
		s.isBaby = p.kid;
	}

	@Override
	protected void scale(AvatarRenderState s, PoseStack poseStack) {
		super.scale(s, poseStack);
		if (s.isBaby) {
			poseStack.scale(0.6f, 0.6f, 0.6f);
		}
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState s) {
		Identifier id = s.getData(SoldierRenderer.SKIN);
		return id != null ? id : SkinBaker.villager(0, "none", "plains");
	}
}
