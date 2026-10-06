package com.stasdoto.airdefense.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class MissileRenderState extends EntityRenderState {
	public final ItemStackRenderState item = new ItemStackRenderState();
	public float yaw;
	public float pitch;
	public float scale = 1;
	public boolean motor;
	/** The engine's glow seen from afar: strength (0 = none), size near by, colour. */
	public float glow;
	public float glowBase;
	public int glowColor;
}
