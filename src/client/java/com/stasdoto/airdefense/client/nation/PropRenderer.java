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
import com.stasdoto.airdefense.client.vehicle.MeshLoader;
import com.stasdoto.airdefense.client.vehicle.MeshModel;
import com.stasdoto.airdefense.client.vehicle.MeshState;
import com.stasdoto.airdefense.nation.PropEntity;

/** 1.41: draws a prop (a lorry, a tractor, a tower crane) at its pose; a crane's top turns ('spin'). */
public class PropRenderer extends EntityRenderer<PropEntity, PropRenderer.State> {
	public static class State extends EntityRenderState {
		String model = "lorry_box";
		float yaw;
		float pitch;
		float spin;
		boolean tall;
		/** 2.0: for a mesh - the paint colour of a town car (-1 = as baked), its wheels' roll and steering. */
		int tint = -1;
		float wheelRoll;
		float steer;
	}

	/** 2.0: a prop drawn from a baked mesh: one model per layer, sharing the mesh's state. */
	private static final class MeshPropModel extends EntityModel<State> {
		private final MeshModel mesh;
		private final ModelPart spin;

		MeshPropModel(MeshModel mesh, java.util.function.Function<Identifier, net.minecraft.client.renderer.rendertype.RenderType> type) {
			super(mesh.root, type);
			this.mesh = mesh;
			this.spin = find(mesh.root, mesh.paths, "spin");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			MeshState ms = mesh.state;
			ms.distance = (float) Math.sqrt(s.distanceToCameraSq);
			ms.tint = s.tint;
			ms.shade = -1;
			ms.trackLeft = s.wheelRoll;
			ms.trackRight = s.wheelRoll;
			mesh.animate(s.wheelRoll, s.steer);
			if (spin != null) {
				spin.yRot = -s.spin * Mth.DEG_TO_RAD;
			}
		}
	}

	/** A mesh's layers (solid, cutout, glass: any may be null) and texture. */
	private record MeshSet(MeshPropModel solid, MeshPropModel cutout, MeshPropModel glass, Identifier texture) {
	}

	/** Which mesh a prop model id is drawn with, and in what paint. */
	private record Kind(String mesh, int tint) {
	}

	/** The town cars' paints (tools/models/boxgen.py STYLES car_*), for the tinted meshes. */
	private static final Map<String, Integer> PAINTS = Map.of("white", 0xE6E6E2, "red", 0xA3261F, "blue", 0x29467E, "green", 0x355E43,
			"beige", 0xCDBE98, "cherry", 0x5E1A24, "black", 0x1C1D20, "silver", 0xA6AAAE);
	private static final Map<String, Kind> KINDS = new HashMap<>();
	private final Map<String, MeshSet> meshes = new HashMap<>();
	private static final MeshSet NONE = new MeshSet(null, null, null, null);
	private int meshGeneration = -1;

	/** "car_saloon_red" -> the car_saloon mesh painted red; anything else -> the mesh of its own id. */
	static Kind kind(String model) {
		Kind k = KINDS.get(model);
		if (k == null) {
			k = new Kind(model, -1);
			int cut = model.lastIndexOf('_');
			if (model.startsWith("car_") && cut > 4) {
				Integer paint = PAINTS.get(model.substring(cut + 1));
				if (paint != null) {
					k = new Kind(model.substring(0, cut), 0xFF000000 | paint);
				}
			}
			KINDS.put(model, k);
		}
		return k;
	}

	private MeshSet meshFor(String meshId) {
		int g = MeshLoader.generation();
		if (g != meshGeneration) {
			meshGeneration = g;
			meshes.clear();
		}
		MeshSet set = meshes.get(meshId);
		if (set == null) {
			MeshLoader.Mesh m = MeshLoader.get(meshId);
			if (m == null) {
				set = NONE;
			} else {
				MeshState st = new MeshState();
				set = new MeshSet(new MeshPropModel(MeshModel.bake(m, MeshLoader.SOLID, st, null), RenderTypes::entityCutoutCull),
						m.has(MeshLoader.CUTOUT) ? new MeshPropModel(MeshModel.bake(m, MeshLoader.CUTOUT, st, null), RenderTypes::entityCutout) : null,
						m.has(MeshLoader.GLASS) ? new MeshPropModel(MeshModel.bake(m, MeshLoader.GLASS, st, null), RenderTypes::entityTranslucentCull) : null,
						m.texture);
			}
			meshes.put(meshId, set);
		}
		return set == NONE ? null : set;
	}

	private static ModelPart find(ModelPart root, Map<String, String> paths, String name) {
		String path = paths.get(name);
		if (path == null) {
			return null;
		}
		ModelPart p = root;
		for (String step : path.split("/")) {
			if (!p.hasChild(step)) {
				return null;
			}
			p = p.getChild(step);
		}
		return p;
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
		java.util.List<String> all = new java.util.ArrayList<>(java.util.List.of(IDS));
		all.addAll(TownTraffic.models());
		for (String id : all) {
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
		s.tint = kind(p.model).tint;
		s.wheelRoll = Mth.lerp(partialTick, p.wheelRollO, p.wheelRoll);
		s.steer = Mth.lerp(partialTick, p.steerVisO, p.steerVis);
		s.lightCoords = LightCoordsUtil.getLightCoords(p.level(), BlockPos.containing(p.getX(), p.getY() + 2, p.getZ()));
	}

	@Override
	protected AABB getBoundingBoxForCulling(PropEntity p, float partialTick) {
		return p.model.equals("tower_crane") ? p.getBoundingBox().inflate(48, 50, 48) : p.model.equals("tram") || p.model.contains("bus")
				? p.getBoundingBox().inflate(9, 8, 9) : p.getBoundingBox().inflate(6, 3, 6);
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		MeshSet mesh = meshFor(kind(s.model).mesh);
		PropModel m = models.get(s.model);
		if (mesh != null) {
			poseStack.pushPose();
			poseStack.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-s.yaw)));
			poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-s.pitch)));
			poseStack.scale(-1, -1, 1);
			collector.submitModel(mesh.solid, s, poseStack, mesh.texture, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
			if (mesh.cutout != null) {
				collector.submitModel(mesh.cutout, s, poseStack, mesh.texture, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
			}
			if (mesh.glass != null) {
				collector.submitModel(mesh.glass, s, poseStack, mesh.texture, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
			}
			poseStack.popPose();
		} else if (m != null) {
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
