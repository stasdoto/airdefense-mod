package com.stasdoto.airdefense.client.vehicle;

/**
 * 2.0: what a mesh's cubes need to know for the thing being drawn now, shared by all of one model's cubes (all its
 * layers). Set in the model's setupAnim from the render state (the game calls setupAnim right before drawing each one).
 */
public final class MeshState {
	/** One track link's length (metres of belt): a belt's texture scrolls one period per link travelled. */
	public static final float TRACK_PITCH = 0.15f;

	/** The paint colour (RGB) for corners flagged as tinted (the town cars' paint); -1 = white (as baked). */
	public int tint = -1;
	/** Everything multiplied by this (ARGB): -1 = as baked; a burnt-out wreck is drawn dark. */
	public int shade = -1;
	/** Belt travel of the left and right tracks (metres, forward positive). */
	public float trackLeft;
	public float trackRight;
	/** Distance from the camera (blocks): parts drawn only near, small bits left out far away. */
	public float distance;

	public void reset() {
		tint = -1;
		shade = -1;
		trackLeft = 0;
		trackRight = 0;
		distance = 0;
	}
}
