package com.stasdoto.airdefense.client.vehicle;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/** Draws a vehicle at real size, tilted to the ground under its wheels. */
public class VehicleRenderer extends EntityRenderer<VehicleEntity, VehicleRenderState> {
	private final VehicleType type;
	private final VehicleModel model;
	private final Identifier texture;
	private final Identifier wreckTexture;
	/** 1.26: the window glass on its own (see-through near the vehicle) and the cab's inside (drawn only up close). */
	private final VehicleModel glass;
	private final VehicleModel glassFar;
	private final VehicleModel inside;
	private final Identifier insideTexture;
	/** Within this distance (blocks) the cab's inside is drawn and the glass is see-through. */
	private static final double INSIDE_RANGE = 18;

	public VehicleRenderer(EntityRendererProvider.Context context, VehicleType type) {
		super(context);
		this.type = type;
		LayerDefinition layer = switch (type) {
			case ISKANDER -> VehicleModels.iskander();
			case KALIBR -> VehicleModels.kalibr();
			case SHAHED -> VehicleModels.shahed();
			case HIMARS -> VehicleModels.himars();
			case PATRIOT -> VehicleModels.patriot();
			case IRIS_T -> VehicleModels.iris_t();
			case NASAMS -> VehicleModels.nasams();
			case GEPARD -> VehicleModels.gepard();
			default -> GenModels.layer(type.id);
		};
		Map<String, String> paths = switch (type) {
			case ISKANDER -> VehicleModels.iskanderPaths();
			case KALIBR -> VehicleModels.kalibrPaths();
			case SHAHED -> VehicleModels.shahedPaths();
			case HIMARS -> VehicleModels.himarsPaths();
			case PATRIOT -> VehicleModels.patriotPaths();
			case IRIS_T -> VehicleModels.iris_tPaths();
			case NASAMS -> VehicleModels.nasamsPaths();
			case GEPARD -> VehicleModels.gepardPaths();
			default -> GenModels.paths(type.id);
		};
		ModelPart root = layer.bakeRoot();
		this.model = new VehicleModel(root, paths, type.geometry);
		this.texture = AirDefense.id("textures/entity/vehicle/" + type.id + ".png");
		this.wreckTexture = AirDefense.id("textures/entity/vehicle/" + type.id + "_wreck.png");
		String gid = type.id + "_glass";
		if (GenModels.has(gid)) {
			glass = new VehicleModel(GenModels.layer(gid).bakeRoot(), GenModels.paths(gid), type.geometry, RenderTypes::entityTranslucentCull);
			glassFar = new VehicleModel(GenModels.layer(gid).bakeRoot(), GenModels.paths(gid), type.geometry);
		} else {
			glass = null;
			glassFar = null;
		}
		String iid = type.id + "_int";
		if (GenModels.has(iid)) {
			inside = new VehicleModel(GenModels.layer(iid).bakeRoot(), GenModels.paths(iid), type.geometry);
			insideTexture = AirDefense.id("textures/entity/vehicle/" + iid + ".png");
		} else {
			inside = null;
			insideTexture = null;
		}
		this.shadowRadius = type.geometry.width() * 0.55f;
		this.shadowStrength = 0.9f;
	}

	@Override
	public VehicleRenderState createRenderState() {
		return new VehicleRenderState();
	}

	@Override
	public void extractRenderState(VehicleEntity v, VehicleRenderState s, float partialTick) {
		super.extractRenderState(v, s, partialTick);
		s.yaw = Mth.rotLerp(partialTick, v.yRotO, v.getYRot());
		s.pitch = Mth.lerp(partialTick, v.tiltPitchO, v.tiltPitch);
		s.roll = Mth.lerp(partialTick, v.tiltRollO, v.tiltRoll);
		s.lift = Mth.lerp(partialTick, v.liftO, v.lift);
		s.wheelRoll = Mth.lerp(partialTick, v.wheelRollO, v.wheelRoll);
		s.steer = Mth.lerp(partialTick, v.steerVisO, v.steerVis);
		s.turretYaw = Mth.rotLerp(partialTick, v.turretYawO, v.turretYaw);
		s.elevation = Mth.lerp(partialTick, v.elevationO, v.elevation);
		s.roofOpen = Mth.lerp(partialTick, v.roofOpenO, v.roofOpen);
		s.radarSpin = Mth.lerp(partialTick, v.radarSpinO, v.radarSpin);
		s.loaded = v.getLoadedMask();
		s.wreck = !v.isAlive();
		s.hidden = GunnerSight.hides(v);
	}

	@Override
	protected AABB getBoundingBoxForCulling(VehicleEntity v, float partialTick) {
		double r = type.geometry.length() / 2 + 1;
		return v.getBoundingBox().inflate(r, 2, r);
	}

	@Override
	public void submit(VehicleRenderState s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (s.hidden) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0, s.lift, 0);
		poseStack.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-s.yaw)));
		poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-s.pitch)));
		poseStack.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(s.roll)));
		poseStack.scale(-1, -1, 1);
		Identifier tex = s.wreck ? wreckTexture : texture;
		boolean near = !s.wreck && s.distanceToCameraSq < INSIDE_RANGE * INSIDE_RANGE;
		int light = s.lightCoords;
		int overlay = OverlayTexture.NO_OVERLAY;
		if (GunnerSight.thermalOn() && !s.wreck) {
			// Through the thermal sight a vehicle shows warm (its engine, its tracks), a burnt-out one cold.
			light = 0xF000F0;
			overlay = OverlayTexture.pack(0.6f, false);
		}
		collector.submitModel(model, s, poseStack, tex, light, overlay, s.outlineColor);
		if (inside != null && near) {
			collector.submitModel(inside, s, poseStack, insideTexture, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
		}
		if (glass != null) {
			// Far away (or with nothing inside to show) the panes stay dark and solid; near, you see in through them.
			collector.submitModel(near && inside != null ? glass : glassFar, s, poseStack, tex, s.lightCoords, OverlayTexture.NO_OVERLAY,
					s.outlineColor);
		}
		poseStack.popPose();
		super.submit(s, poseStack, collector, camera);
	}
}
