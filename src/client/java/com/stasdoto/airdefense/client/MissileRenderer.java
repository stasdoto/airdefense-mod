package com.stasdoto.airdefense.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

import com.stasdoto.airdefense.missile.MissileEntity;

/**
 * Draws a missile using its item's 3D block-style model, pointed along its flight direction.
 * The item models are built nose-forward along +Z, centred on the origin.
 */
public class MissileRenderer extends EntityRenderer<MissileEntity, MissileRenderState> {
	private final ItemModelResolver itemModelResolver;

	public MissileRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0;
	}

	@Override
	public MissileRenderState createRenderState() {
		return new MissileRenderState();
	}

	@Override
	public void extractRenderState(MissileEntity entity, MissileRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
		state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
		state.scale = entity.getMissileType().renderScale;
		state.motor = entity.isMotorOn();
		itemModelResolver.updateForNonLiving(state.item, entity.getDisplayStack(), ItemDisplayContext.NONE, entity);
	}

	@Override
	protected int getBlockLightLevel(MissileEntity entity, BlockPos pos) {
		// A burning rocket motor lights itself up.
		return entity.isMotorOn() ? 15 : super.getBlockLightLevel(entity, pos);
	}

	@Override
	protected boolean affectedByCulling(MissileEntity entity) {
		return false;
	}

	@Override
	public void submit(MissileRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0, state.boundingBoxHeight / 2, 0);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitch));
		poseStack.scale(state.scale, state.scale, state.scale);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
