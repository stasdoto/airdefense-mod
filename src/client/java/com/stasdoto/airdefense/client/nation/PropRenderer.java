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
import com.stasdoto.airdefense.nation.PropEntity;

/** 1.41: draws a prop (a lorry, a tractor, a tower crane) at its pose; a crane's top turns ('spin'). */
public class PropRenderer extends EntityRenderer<PropEntity, PropRenderer.State> {
	public static class State extends EntityRenderState {
		String model = "lorry_box";
		float yaw;
		float pitch;
		float spin;
		boolean tall;
	}

	private static final class PropModel extends EntityModel<State> {
		private final ModelPart spin;

		PropModel(ModelPart root, Map<String, String> paths) {
			super(root, RenderTypes::entityCutoutCull);
			ModelPart p = null;
			String path = paths.get("spin");
			if (path != null) {
				p = root;
				for (String name : path.split("/")) {
					p = p.getChild(name);
				}
			}
			this.spin = p;
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			if (spin != null) {
				spin.yRot = -s.spin * Mth.DEG_TO_RAD;
			}
		}
	}

	private static final String[] IDS = {"lorry_box", "lorry_tank", "tipper", "tractor", "tower_crane"};
	private final Map<String, PropModel> models = new HashMap<>();
	private final Map<String, Identifier> textures = new HashMap<>();

	public PropRenderer(EntityRendererProvider.Context context) {
		super(context);
		for (String id : IDS) {
			models.put(id, new PropModel(GenModels.layer(id).bakeRoot(), GenModels.paths(id)));
			textures.put(id, AirDefense.id("textures/entity/vehicle/" + id + ".png"));
		}
		this.shadowRadius = 1.2f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PropEntity p, State s, float partialTick) {
		super.extractRenderState(p, s, partialTick);
		s.model = p.model;
		s.yaw = Mth.rotLerp(partialTick, (float) p.poseO[3], (float) p.pose[3]);
		s.pitch = (float) Mth.lerp(partialTick, p.poseO[4], p.pose[4]);
		s.spin = Mth.rotLerp(partialTick, p.spinO, p.spin);
		s.tall = p.model.equals("tower_crane");
		s.lightCoords = LightCoordsUtil.getLightCoords(p.level(), BlockPos.containing(p.getX(), p.getY() + 2, p.getZ()));
	}

	@Override
	protected AABB getBoundingBoxForCulling(PropEntity p, float partialTick) {
		return p.model.equals("tower_crane") ? p.getBoundingBox().inflate(48, 50, 48) : p.getBoundingBox().inflate(6, 3, 6);
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		PropModel m = models.get(s.model);
		if (m != null) {
			poseStack.pushPose();
			poseStack.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-s.yaw)));
			poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-s.pitch)));
			poseStack.scale(-1, -1, 1);
			collector.submitModel(m, s, poseStack, textures.get(s.model), s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
			poseStack.popPose();
		}
		super.submit(s, poseStack, collector, camera);
	}
}
