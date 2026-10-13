package com.stasdoto.airdefense.client.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.stasdoto.airdefense.vehicle.VehicleGeometry;

/** Animates a generated vehicle model: rolling and steering wheels, turret, erector/launcher, roof doors, radar, missiles. */
public class VehicleModel extends EntityModel<VehicleRenderState> {
	private record Wheel(ModelPart part, float radius, boolean steer) {
	}

	private final List<Wheel> wheels = new ArrayList<>();
	private final ModelPart turret;
	private final ModelPart elevator;
	private final ModelPart spinner;
	private final ModelPart[] openParts;
	private final ModelPart[] railParts;
	/** The steering wheel in the cab (1.26), turning with the front wheels. */
	private final ModelPart steeringWheel;
	/** 2.0: drawn from a baked mesh (its own rolling and steering parts, tint, tracks, near parts), or null for boxes. */
	private MeshModel mesh;
	/** The colour a burnt-out vehicle's mesh is drawn in (instead of the box models' _wreck texture). */
	public static final int WRECK_SHADE = 0xFF2E2A26;
	/** The paint of a tinted mesh (-1 = white as baked). */
	public int tint = -1;

	public VehicleModel(ModelPart root, Map<String, String> paths, VehicleGeometry.Geometry g) {
		// Back faces culled: from the driver's seat you look out through the cab instead of at its inside.
		this(root, paths, g, RenderTypes::entityCutoutCull);
	}

	/** A model drawn its own way: the window glass see-through, the inside of a cab. */
	public VehicleModel(ModelPart root, Map<String, String> paths, VehicleGeometry.Geometry g, Function<Identifier, RenderType> renderType) {
		super(root, renderType);
		steeringWheel = find(root, paths, "steering_wheel");
		for (VehicleGeometry.Wheel w : g.wheels()) {
			ModelPart p = find(root, paths, w.part());
			if (p != null) {
				wheels.add(new Wheel(p, w.radius(), w.steer()));
			}
		}
		turret = g.turret() != null ? find(root, paths, g.turret()) : null;
		elevator = g.elevator() != null ? find(root, paths, g.elevator()) : null;
		spinner = g.spinner() != null ? find(root, paths, g.spinner()) : null;
		openParts = new ModelPart[g.openParts().length];
		for (int i = 0; i < openParts.length; i++) {
			openParts[i] = find(root, paths, g.openParts()[i]);
		}
		railParts = new ModelPart[g.rails().length];
		for (int i = 0; i < railParts.length; i++) {
			String name = g.rails()[i].part();
			// Only exposed missiles and drones disappear when fired; canisters and pods stay.
			railParts[i] = name.startsWith("missile_") || name.startsWith("drone_") ? find(root, paths, name) : null;
		}
	}

	/** 2.0: one layer of a baked mesh (see {@link MeshModel}), animated like the box model by its parts' names. */
	public static VehicleModel ofMesh(MeshModel mesh, VehicleGeometry.Geometry g, Function<Identifier, RenderType> renderType) {
		VehicleModel m = new VehicleModel(mesh.root, mesh.paths, g, renderType);
		m.mesh = mesh;
		return m;
	}

	/** The names of the parts the geometry animates as wheels (the mesh leaves them to the model). */
	public static java.util.Set<String> wheelNames(VehicleGeometry.Geometry g) {
		java.util.Set<String> out = new java.util.HashSet<>();
		for (VehicleGeometry.Wheel w : g.wheels()) {
			out.add(w.part());
		}
		return out;
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

	@Override
	public void setupAnim(VehicleRenderState s) {
		super.setupAnim(s);
		if (mesh != null) {
			MeshState ms = mesh.state;
			ms.distance = (float) Math.sqrt(s.distanceToCameraSq);
			ms.shade = s.wreck ? WRECK_SHADE : -1;
			ms.tint = tint;
			ms.trackLeft = s.wheelRoll + s.trackTurn;
			ms.trackRight = s.wheelRoll - s.trackTurn;
			mesh.animate(s.wheelRoll, s.steer);
		}
		for (Wheel w : wheels) {
			w.part.xRot = -s.wheelRoll / w.radius;
			if (w.steer) {
				w.part.yRot = s.steer * Mth.DEG_TO_RAD;
			}
		}
		if (turret != null) {
			turret.yRot = s.turretYaw * Mth.DEG_TO_RAD;
		}
		if (elevator != null) {
			elevator.xRot = s.elevation * Mth.DEG_TO_RAD;
		}
		if (spinner != null) {
			spinner.yRot = s.radarSpin;
		}
		if (steeringWheel != null) {
			// About three turns of the wheel for the road wheels' full lock.
			steeringWheel.zRot = s.steer * 3.2f * Mth.DEG_TO_RAD;
		}
		for (ModelPart p : openParts) {
			if (p != null) {
				boolean left = p.x < 0;
				// Hinged at the outer edge: the left half swings up and out to the left, the right one to the right.
				p.zRot = (left ? -110 : 110) * s.roofOpen * Mth.DEG_TO_RAD;
			}
		}
		for (int i = 0; i < railParts.length; i++) {
			if (railParts[i] != null) {
				railParts[i].visible = (s.loaded & (1 << i)) != 0;
			}
		}
	}
}
