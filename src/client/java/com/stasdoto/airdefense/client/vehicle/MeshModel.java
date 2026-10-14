package com.stasdoto.airdefense.client.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

/**
 * 2.0: a mesh's layer as an ordinary ModelPart tree (each part posed like the box models': its pivot relative to its
 * parent's, in 1/16 blocks, y down; its fixed rotation in radians), with a {@link MeshCube} in each part that has
 * something in that layer. Also the mesh's own animation: rolling parts that are not among the geometry's wheels (a
 * tank's road wheels, sprockets) and steering parts.
 */
public final class MeshModel {
	public final MeshLoader.Mesh mesh;
	public final int layerType;
	public final ModelPart root;
	/** Part name -> path of names from the root ("body/wheel_fl"), as the generated box models give them. */
	public final Map<String, String> paths;
	public final MeshState state;
	private final ModelPart[] rolling;
	private final float[] radii;
	private final ModelPart[] steering;

	private MeshModel(MeshLoader.Mesh mesh, int layerType, ModelPart root, Map<String, String> paths, MeshState state, ModelPart[] rolling,
			float[] radii, ModelPart[] steering) {
		this.mesh = mesh;
		this.layerType = layerType;
		this.root = root;
		this.paths = paths;
		this.state = state;
		this.rolling = rolling;
		this.radii = radii;
		this.steering = steering;
	}

	/**
	 * Builds the tree of one layer ({@link MeshLoader#SOLID}, GLASS, CUTOUT). {@code animated}: the names of parts animated
	 * by someone else (the geometry's wheels), left out of the mesh's own rolling and steering.
	 */
	public static MeshModel bake(MeshLoader.Mesh mesh, int layerType, MeshState state, Set<String> animated) {
		MeshLoader.Part[] parts = mesh.parts;
		int n = parts.length;
		List<List<Integer>> children = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			children.add(new ArrayList<>());
		}
		List<Integer> roots = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (parts[i].parent < 0) {
				roots.add(i);
			} else {
				children.get(parts[i].parent).add(i);
			}
		}
		ModelPart[] built = new ModelPart[n];
		String[] keys = new String[n];
		Map<String, String> paths = new HashMap<>();
		// Children first (a ModelPart takes its children when made): parts come after their parents in the file.
		for (int i = n - 1; i >= 0; i--) {
			MeshLoader.Part p = parts[i];
			List<ModelPart.Cube> cubes = new ArrayList<>(1);
			MeshLoader.Layer l = p.layer(layerType);
			if (l != null && l.quads.length > 0) {
				cubes.add(new MeshCube(l, state, p.track));
			}
			Map<String, ModelPart> kids = new LinkedHashMap<>();
			for (int c : children.get(i)) {
				keys[c] = unique(kids, parts[c].name);
				kids.put(keys[c], built[c]);
			}
			ModelPart mp = new ModelPart(cubes, kids);
			float[] pp = p.parent >= 0 ? parts[p.parent].pivot : new float[3];
			mp.setInitialPose(PartPose.offsetAndRotation((p.pivot[0] - pp[0]) * 16, -(p.pivot[1] - pp[1]) * 16, (p.pivot[2] - pp[2]) * 16,
					p.rot[0] * Mth.DEG_TO_RAD, p.rot[1] * Mth.DEG_TO_RAD, p.rot[2] * Mth.DEG_TO_RAD));
			mp.resetPose();
			built[i] = mp;
		}
		Map<String, ModelPart> top = new LinkedHashMap<>();
		for (int r : roots) {
			keys[r] = unique(top, parts[r].name);
			top.put(keys[r], built[r]);
		}
		ModelPart root = new ModelPart(List.of(), top);
		List<ModelPart> rolling = new ArrayList<>();
		List<Float> radii = new ArrayList<>();
		List<ModelPart> steering = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			MeshLoader.Part p = parts[i];
			paths.putIfAbsent(p.name, path(parts, keys, i));
			if (animated != null && animated.contains(p.name)) {
				continue;
			}
			if (p.spin > 0) {
				rolling.add(built[i]);
				radii.add(p.spin);
			}
			if (p.steer) {
				steering.add(built[i]);
			}
		}
		float[] r = new float[radii.size()];
		for (int i = 0; i < r.length; i++) {
			r[i] = radii.get(i);
		}
		return new MeshModel(mesh, layerType, root, paths, state, rolling.toArray(new ModelPart[0]), r, steering.toArray(new ModelPart[0]));
	}

	private static String unique(Map<String, ModelPart> kids, String name) {
		String k = name;
		for (int i = 2; kids.containsKey(k); i++) {
			k = name + "_" + i;
		}
		return k;
	}

	private static String path(MeshLoader.Part[] parts, String[] keys, int i) {
		StringBuilder sb = new StringBuilder(keys[i]);
		for (int p = parts[i].parent; p >= 0; p = parts[p].parent) {
			sb.insert(0, keys[p] + "/");
		}
		return sb.toString();
	}

	/** The mesh's own rolling (wheel roll, metres travelled) and steering (degrees); after the pose is reset. */
	public void animate(float wheelRoll, float steerDeg) {
		for (int i = 0; i < rolling.length; i++) {
			rolling[i].xRot = -wheelRoll / radii[i];
		}
		float yr = steerDeg * Mth.DEG_TO_RAD;
		for (ModelPart p : steering) {
			p.yRot = yr;
		}
	}
}
