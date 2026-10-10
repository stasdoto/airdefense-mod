package com.stasdoto.airdefense.client.nation;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.client.vehicle.GenModels;
import com.stasdoto.airdefense.nation.PlaneEntity;

/** 1.40: draws an airliner at its pose; when it is under way its red beacon flashes (on top and under the belly). */
public class PlaneRenderer extends EntityRenderer<PlaneEntity, PlaneRenderer.State> {
	public static class State extends EntityRenderState {
		String model = "airliner";
		float yaw;
		float pitch;
		boolean beacon;
		int flash;
	}

	private static final class PlaneModel extends EntityModel<State> {
		PlaneModel(net.minecraft.client.model.geom.ModelPart root) {
			super(root, RenderTypes::entityCutoutCull);
		}
	}

	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(AirDefense.id("textures/misc/glow_dot.png"));
	private static final String[] IDS = {"airliner", "turboprop"};
	private final Map<String, PlaneModel> models = new HashMap<>();
	private final Map<String, Identifier> textures = new HashMap<>();

	public PlaneRenderer(EntityRendererProvider.Context context) {
		super(context);
		for (String id : IDS) {
			models.put(id, new PlaneModel(GenModels.layer(id).bakeRoot()));
			textures.put(id, AirDefense.id("textures/entity/vehicle/" + id + ".png"));
		}
		this.shadowRadius = 3.5f;
		this.shadowStrength = 0.6f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PlaneEntity p, State s, float partialTick) {
		super.extractRenderState(p, s, partialTick);
		s.model = p.model;
		s.yaw = Mth.rotLerp(partialTick, (float) p.poseO[3], (float) p.pose[3]);
		s.pitch = (float) Mth.lerp(partialTick, p.poseO[4], p.pose[4]);
		s.beacon = p.phase != 1;
		s.flash = p.tickCount;
		s.lightCoords = LightCoordsUtil.getLightCoords(p.level(), BlockPos.containing(p.getX(), p.getY() + 3, p.getZ()));
	}

	@Override
	protected AABB getBoundingBoxForCulling(PlaneEntity p, float partialTick) {
		return p.getBoundingBox().inflate(20, 8, 20);
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		PlaneModel m = models.get(s.model);
		if (m != null) {
			poseStack.pushPose();
			poseStack.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-s.yaw)));
			poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-s.pitch)));
			poseStack.scale(-1, -1, 1);
			collector.submitModel(m, s, poseStack, textures.get(s.model), s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
			poseStack.popPose();
			if (s.beacon && (s.flash / 6) % 4 == 0) {
				boolean jet = s.model.equals("airliner");
				beacon(s, poseStack, collector, camera, jet ? 6.0 : 3.85, jet ? -1.8 : 0.2);
				beacon(s, poseStack, collector, camera, jet ? 1.85 : 0.9, jet ? -1.8 : 0.2);
			}
		}
		super.submit(s, poseStack, collector, camera);
	}

	/** A red glowing dot (the anti-collision beacon) at height h, z along the plane. */
	private void beacon(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, double h, double z) {
		float yaw = s.yaw * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw);
		double fz = Mth.cos(yaw);
		float size = (float) (0.35 + Math.sqrt(s.distanceToCameraSq) * 0.006);
		poseStack.pushPose();
		poseStack.translate(fx * z, h + Math.sin(Math.toRadians(s.pitch)) * z, fz * z);
		poseStack.mulPose(new Matrix4f().rotation(camera.orientation));
		collector.submitCustomGeometry(poseStack, GLOW, (pose, vc) -> {
			float[][] corners = {{size, -size, 1, 1}, {size, size, 1, 0}, {-size, size, 0, 0}, {-size, -size, 0, 1}};
			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < 4; i++) {
					float[] c = corners[pass == 0 ? i : 3 - i];
					vc.addVertex(pose, c[0], c[1], 0).setColor(255, 40, 30, 235).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(0xF000F0).setNormal(pose, 0, 0, 1);
				}
			}
		});
		poseStack.popPose();
	}
}
