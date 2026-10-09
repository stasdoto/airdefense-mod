package com.stasdoto.airdefense.client.gear;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import com.stasdoto.airdefense.AirDefense;

/**
 * 1.27: one piece of gear (a helmet, a vest, a pouch) from tools/models/gear.py. The state passed when it is drawn is
 * the angle of the night goggles' mount (0 = down over the eyes); only the FAST with goggles has that part.
 */
public final class GearModel extends Model<Float> {
	private static final Map<String, GearModel> CACHE = new HashMap<>();

	public final Identifier texture;
	private final ModelPart nvg;

	private GearModel(String id) {
		super(GenGear.layer(id).bakeRoot(), RenderTypes::entityCutout);
		texture = AirDefense.id("textures/entity/gear/" + id + ".png");
		ModelPart found = null;
		String path = GenGear.paths(id).get("nvg");
		if (path != null) {
			ModelPart p = root;
			for (String step : path.split("/")) {
				p = p.hasChild(step) ? p.getChild(step) : null;
				if (p == null) {
					break;
				}
			}
			found = p;
		}
		nvg = found;
	}

	public static GearModel get(String id) {
		return CACHE.computeIfAbsent(id, GearModel::new);
	}

	@Override
	public void setupAnim(Float flip) {
		super.setupAnim(flip);
		if (nvg != null) {
			nvg.xRot = flip;
		}
	}
}
