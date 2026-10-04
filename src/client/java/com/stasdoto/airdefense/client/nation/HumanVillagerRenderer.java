package com.stasdoto.airdefense.client.nation;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;

import com.stasdoto.airdefense.nation.Nations;

/**
 * Villagers drawn as people: every one with his own face, skin tone and hair (see {@link SkinBaker}), dressed for
 * his trade and for the climate his village lives in. Only the look changes - trading, work and everything else are
 * the game's own.
 */
public class HumanVillagerRenderer extends HumanoidMobRenderer<Villager, AvatarRenderState, PlayerModel> {
	public HumanVillagerRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(Villager v, AvatarRenderState s, float partialTick) {
		super.extractRenderState(v, s, partialTick);
		VillagerData d = v.getVillagerData();
		String profession = d.profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
		String type = d.type().unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
		s.setData(SoldierRenderer.SKIN, SkinBaker.villager(Nations.lookOf(v.getUUID()), profession, type));
	}

	@Override
	protected void scale(AvatarRenderState s, PoseStack poseStack) {
		super.scale(s, poseStack);
		if (s.isBaby && s.ageScale > 0.99f) {
			poseStack.scale(0.55f, 0.55f, 0.55f);
		}
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState s) {
		Identifier id = s.getData(SoldierRenderer.SKIN);
		return id != null ? id : SkinBaker.villager(0, "none", "plains");
	}
}
