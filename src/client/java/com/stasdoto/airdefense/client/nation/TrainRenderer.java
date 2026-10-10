package com.stasdoto.airdefense.client.nation;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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
import com.stasdoto.airdefense.nation.TrainEntity;

/** 1.39: draws a train - each car at its own place on the rails, turned along them and tilted on the grades. */
public class TrainRenderer extends EntityRenderer<TrainEntity, TrainRenderer.State> {
	public static class State extends EntityRenderState {
		String[] cars = new String[0];
		boolean[] flip = new boolean[0];
		/** Each car: x, y, z (from the train's own position), yaw, pitch. */
		float[][] pose = new float[0][];
		int[] light = new int[0];
	}

	private static final class CarModel extends EntityModel<State> {
		CarModel(ModelPart root) {
			super(root, RenderTypes::entityCutoutCull);
		}
	}

	private static final String[] IDS = {"emu_head", "emu_car", "loco", "boxcar", "tank_car", "gondola"};
	private final Map<String, CarModel> models = new HashMap<>();
	private final Map<String, Identifier> textures = new HashMap<>();

	public TrainRenderer(EntityRendererProvider.Context context) {
		super(context);
		for (String id : IDS) {
			models.put(id, new CarModel(GenModels.layer(id).bakeRoot()));
			textures.put(id, AirDefense.id("textures/entity/vehicle/" + id + ".png"));
		}
		this.shadowRadius = 0;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TrainEntity t, State s, float partialTick) {
		super.extractRenderState(t, s, partialTick);
		int n = Math.min(t.cars.size(), Math.min(t.pose.length, t.poseO.length));
		s.cars = new String[n];
		s.flip = new boolean[n];
		s.pose = new float[n][];
		s.light = new int[n];
		for (int i = 0; i < n; i++) {
			double[] a = t.poseO[i];
			double[] b = t.pose[i];
			double x = Mth.lerp(partialTick, a[0], b[0]);
			double y = Mth.lerp(partialTick, a[1], b[1]);
			double z = Mth.lerp(partialTick, a[2], b[2]);
			s.cars[i] = t.cars.get(i);
			s.flip[i] = t.flipped.get(i);
			s.pose[i] = new float[]{(float) (x - s.x), (float) (y - s.y), (float) (z - s.z), Mth.rotLerp(partialTick, (float) a[3], (float) b[3]),
					(float) Mth.lerp(partialTick, a[4], b[4])};
			s.light[i] = LightCoordsUtil.getLightCoords(t.level(), BlockPos.containing(x, y + 2.5, z));
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(TrainEntity t, float partialTick) {
		if (t.pose.length == 0) {
			return t.getBoundingBox();
		}
		double x0 = Double.MAX_VALUE;
		double y0 = Double.MAX_VALUE;
		double z0 = Double.MAX_VALUE;
		double x1 = -Double.MAX_VALUE;
		double y1 = -Double.MAX_VALUE;
		double z1 = -Double.MAX_VALUE;
		for (double[] p : t.pose) {
			x0 = Math.min(x0, p[0]);
			y0 = Math.min(y0, p[1]);
			z0 = Math.min(z0, p[2]);
			x1 = Math.max(x1, p[0]);
			y1 = Math.max(y1, p[1]);
			z1 = Math.max(z1, p[2]);
		}
		return new AABB(x0 - 12, y0 - 1, z0 - 12, x1 + 12, y1 + 6, z1 + 12);
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < s.cars.length; i++) {
			CarModel m = models.get(s.cars[i]);
			if (m == null) {
				continue;
			}
			float[] p = s.pose[i];
			poseStack.pushPose();
			poseStack.translate(p[0], p[1], p[2]);
			poseStack.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-p[3] + (s.flip[i] ? 180 : 0))));
			poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(s.flip[i] ? p[4] : -p[4])));
			poseStack.scale(-1, -1, 1);
			collector.submitModel(m, s, poseStack, textures.get(s.cars[i]), s.light[i], OverlayTexture.NO_OVERLAY, s.outlineColor);
			poseStack.popPose();
		}
		super.submit(s, poseStack, collector, camera);
	}
}
