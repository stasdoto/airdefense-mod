package com.stasdoto.airdefense.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;

import com.stasdoto.airdefense.gear.Pouch;
import com.stasdoto.airdefense.gear.Pouches;
import com.stasdoto.airdefense.registry.ModItems;
import com.stasdoto.airdefense.weapon.NvgItem;

/**
 * 1.27: helmets, vests and the pouches on them drawn in 3D on anyone with a human-shaped body (players, soldiers,
 * workers, zombies, armour stands). The pieces follow the head and the body; the goggles of the FAST flip up and down.
 */
public class GearLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
	/** The goggles flipped up onto the helmet (radians about the mount's hinge). */
	public static final float NVG_UP = (float) Math.toRadians(104);
	private static final float INV = 1.0f / GenGear.SCALE;

	/** The pouch slots (the person's pixels, body frame: x right, y up from the neck, z out of the chest), see gear.py. */
	private static final float[][] PC_SLOTS = {{-2.35f, -8.45f, 3.35f, 0}, {0f, -8.45f, 3.35f, 0}, {2.35f, -8.45f, 3.35f, 0},
			{0f, -5.2f, 3.35f, 0}, {-1.7f, -8.8f, -3.3f, 180}, {1.7f, -8.8f, -3.3f, 180}};
	private static final float[][] HEAVY_SLOTS = {{-2.5f, -9.6f, 3.55f, 0}, {0f, -9.6f, 3.55f, 0}, {2.5f, -9.6f, 3.55f, 0},
			{0f, -6.2f, 3.55f, 0}, {-1.8f, -9.6f, -3.5f, 180}, {1.8f, -9.6f, -3.5f, 180}};

	/** For the automated test: pieces drawn. */
	public static int drawn;

	public GearLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	public static String helmetModel(ItemStack head) {
		if (head.is(ModItems.HELMET)) {
			return "helmet_6b47";
		}
		if (head.is(ModItems.HELMET_FAST)) {
			return "helmet_fast";
		}
		if (head.is(ModItems.NVG_HELMET)) {
			return "helmet_fast_nvg";
		}
		return null;
	}

	public static String vestModel(ItemStack chest) {
		if (chest.is(ModItems.VEST)) {
			return "vest_pc";
		}
		if (chest.is(ModItems.VEST_HEAVY)) {
			return "vest_6b45";
		}
		return null;
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
		if (state.isBaby) {
			return;
		}
		M parent = getParentModel();
		String helmet = helmetModel(state.headEquipment);
		if (helmet != null) {
			pose.pushPose();
			parent.root().translateAndRotate(pose);
			parent.head.translateAndRotate(pose);
			pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(180)));
			pose.scale(INV, INV, INV);
			GearModel m = GearModel.get(helmet);
			float flip = NvgItem.isOn(state.headEquipment) ? 0f : NVG_UP;
			collector.submitModel(m, flip, pose, m.texture, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			pose.popPose();
			drawn++;
		}
		String vest = vestModel(state.chestEquipment);
		if (vest != null) {
			pose.pushPose();
			parent.root().translateAndRotate(pose);
			parent.body.translateAndRotate(pose);
			pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(180)));
			pose.pushPose();
			pose.scale(INV, INV, INV);
			GearModel m = GearModel.get(vest);
			collector.submitModel(m, 0f, pose, m.texture, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			pose.popPose();
			drawn++;
			boolean heavy = vest.equals("vest_6b45");
			float[][] slots = heavy ? HEAVY_SLOTS : PC_SLOTS;
			String cloth = heavy ? "emr" : "mc";
			int packed = Pouches.packed(state.chestEquipment);
			for (int i = 0; i < Pouches.SLOTS; i++) {
				Pouch p = Pouches.at(packed, i);
				if (p == Pouch.NONE) {
					continue;
				}
				float[] s = slots[i];
				pose.pushPose();
				// Gear space keeps the generator's axes with y turned down (see GenGear).
				pose.translate(s[0] / 16f, -s[1] / 16f, s[2] / 16f);
				if (s[3] != 0) {
					pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(s[3])));
				}
				pose.scale(INV, INV, INV);
				GearModel pm = GearModel.get("pouch_" + p.name().toLowerCase(java.util.Locale.ROOT) + "_" + cloth);
				collector.submitModel(pm, 0f, pose, pm.texture, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
				pose.popPose();
				drawn++;
			}
			pose.popPose();
		}
	}
}
