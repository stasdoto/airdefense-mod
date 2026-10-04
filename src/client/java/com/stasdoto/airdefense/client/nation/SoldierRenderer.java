package com.stasdoto.airdefense.client.nation;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

import com.stasdoto.airdefense.nation.SoldierEntity;
import com.stasdoto.airdefense.weapon.GunItem;

/** Guards, soldiers and bandits: a person (see {@link SkinBaker}) with helmet, vest and a gun held in both hands. */
public class SoldierRenderer extends HumanoidMobRenderer<SoldierEntity, AvatarRenderState, PlayerModel> {
	static final RenderStateDataKey<Identifier> SKIN = RenderStateDataKey.create(() -> "airdefense:person_skin");

	public SoldierRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
		addLayer(new HumanoidArmorLayer<AvatarRenderState, PlayerModel, HumanoidModel<AvatarRenderState>>(this,
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), HumanoidModel::new), ctx.getEquipmentRenderer()));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(SoldierEntity e, AvatarRenderState s, float partialTick) {
		super.extractRenderState(e, s, partialTick);
		s.setData(SKIN, SkinBaker.soldier(e.look(), e.role(), e.color()));
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(SoldierEntity mob, HumanoidArm arm) {
		if (mob.getMainHandItem().getItem() instanceof GunItem g && (g.gun.longGun() || mob.isAggressive())) {
			return arm == mob.getMainArm() ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.EMPTY;
		}
		return super.getArmPose(mob, arm);
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState s) {
		Identifier id = s.getData(SKIN);
		return id != null ? id : SkinBaker.soldier(0, SoldierEntity.GUARD, -1);
	}
}
