package com.stasdoto.airdefense.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;

/**
 * Draws a missile using its item's 3D block-style model, pointed along its flight direction.
 * The item models are built nose-forward along +Z, centred on the origin.
 */
public class MissileRenderer extends EntityRenderer<MissileEntity, MissileRenderState> {
	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(AirDefense.id("textures/misc/glow_dot.png"));
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
		MissileType type = entity.getMissileType();
		// Engines that burn glow: rockets while the motor runs, jets and drone engines (exhaust) all the way.
		boolean burning = entity.isMotorOn() || type.kind == MissileType.Kind.CRUISE || type.kind == MissileType.Kind.DRONE;
		if (!burning || type.kind == MissileType.Kind.DIRECT || type.track()) {
			state.glow = 0;
		} else if (type.kind == MissileType.Kind.DRONE) {
			state.glow = 0.75f;
			state.glowBase = 0.55f;
			state.glowColor = 0xFFC060;
		} else {
			state.glow = 1.0f;
			state.glowBase = type.kind == MissileType.Kind.INTERCEPTOR ? 0.6f : 0.85f;
			state.glowColor = 0xFFE9A0;
		}
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
		poseStack.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(state.yaw)));
		poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-state.pitch)));
		poseStack.scale(state.scale, state.scale, state.scale);
		// Through a thermal sight a flying drone or missile glows (its engine, its motor).
		boolean hot = com.stasdoto.airdefense.client.vehicle.ThermalView.wanted();
		state.item.submit(poseStack, collector, hot ? 0xF000F0 : state.lightCoords, hot ? OverlayTexture.pack(1.0f, false) : OverlayTexture.NO_OVERLAY,
				state.outlineColor);
		poseStack.popPose();
		if (state.glow > 0) {
			glow(state, poseStack, collector, camera);
		}
		super.submit(state, poseStack, collector, camera);
	}

	/**
	 * The burning engine seen from afar: a bright yellow point at the tail that keeps about the same size on screen
	 * however far away it is, so a missile or a drone at night is a moving spark in the sky.
	 */
	private static void glow(MissileRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		double dist = Math.sqrt(state.distanceToCameraSq);
		float size = (float) (state.glowBase + dist * 0.011) * state.glow;
		float yaw = state.yaw * Mth.DEG_TO_RAD;
		float pitch = state.pitch * Mth.DEG_TO_RAD;
		float back = 1.3f * state.scale;
		poseStack.pushPose();
		poseStack.translate(-Mth.sin(yaw) * Mth.cos(pitch) * back, state.boundingBoxHeight / 2 - Mth.sin(pitch) * back,
				-Mth.cos(yaw) * Mth.cos(pitch) * back);
		poseStack.mulPose(new Matrix4f().rotation(camera.orientation));
		int color = state.glowColor;
		int a = (int) (Mth.clamp(state.glow, 0, 1) * 255);
		int r = (color >> 16) & 255;
		int g = (color >> 8) & 255;
		int b = color & 255;
		collector.submitCustomGeometry(poseStack, GLOW, (pose, vc) -> {
			// Both windings: seen from any side whatever the culling.
			float[][] corners = {{size, -size, 1, 1}, {size, size, 1, 0}, {-size, size, 0, 0}, {-size, -size, 0, 1}};
			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < 4; i++) {
					float[] c = corners[pass == 0 ? i : 3 - i];
					vc.addVertex(pose, c[0], c[1], 0).setColor(r, g, b, a).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(0xF000F0).setNormal(pose, 0, 0, 1);
				}
			}
		});
		poseStack.popPose();
	}
}
