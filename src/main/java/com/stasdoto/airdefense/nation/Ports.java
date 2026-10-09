package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 1.28: a town by the sea or a wide river gets a port on its shore: a concrete quay with warehouses behind it,
 * container stacks, two piers out into the water with a ship-to-shore crane at each, a cargo ship moored alongside, a
 * lighthouse at the end of the outer pier, and a road into town. Planned from the seed like the depots (only in
 * worlds with the 1.28 town styles); the ground is laid by CityGen, the rest comes from {@link Port#blocks}.
 *
 * Port space: u runs from the land out to sea along {@link Port#sea}, v across it; both are world coordinates turned.
 */
public final class Ports {
	/** Across the shore, each way from the middle. */
	static final int HALF = 30;
	/** From the shore line inland to the back of the port. */
	static final int LAND = 30;
	/** The quay reaches this far into the water past the shore line. */
	static final int APRON = 6;
	static final int PIER_LEN = 34;
	static final int BLEND = 6;

	public static final class Port {
		public final Cities.City city;
		/** From the land towards the water. */
		public final Direction sea;
		/** The shore line, the quay's water face and the back of the port (u). */
		public final int shore;
		public final int face;
		public final int back;
		/** The middle across (v). */
		public final int mid;
		/** The quay's level (its top block), the sea level and the sea floor in front of it. */
		public final int y;
		public final int sea_;
		public final int floor;
		public final int[] piers;
		public final List<Building> buildings = new ArrayList<>();
		public Cities.Road access;
		/** World bounds of everything. */
		public final int x0;
		public final int z0;
		public final int x1;
		public final int z1;
		private volatile Map<Long, List<long[]>> blocks;
		private final List<BlockState> palette = new ArrayList<>();

		Port(Cities.City city, Direction sea, int shore, int mid, int y, int seaLevel, int floor) {
			this.city = city;
			this.sea = sea;
			this.shore = shore;
			this.face = shore + APRON;
			this.back = shore - LAND;
			this.mid = mid;
			this.y = y;
			this.sea_ = seaLevel;
			this.floor = floor;
			this.piers = new int[]{mid - 15, mid + 15};
			int[] a = world(back - BLEND - 2, mid - HALF - BLEND - 14);
			int[] b = world(face + PIER_LEN + 6, mid + HALF + BLEND + 2);
			x0 = Math.min(a[0], b[0]);
			x1 = Math.max(a[0], b[0]);
			z0 = Math.min(a[1], b[1]);
			z1 = Math.max(a[1], b[1]);
		}

		public long key() {
			return city.key() * 8 + 6;
		}

		/** Port space to world: {x, z}. */
		public int[] world(int u, int v) {
			return switch (sea) {
				case EAST -> new int[]{u, v};
				case WEST -> new int[]{-u, v};
				case SOUTH -> new int[]{v, u};
				default -> new int[]{v, -u};
			};
		}

		/** World to port space: {u, v}. */
		public int[] local(int x, int z) {
			return switch (sea) {
				case EAST -> new int[]{x, z};
				case WEST -> new int[]{-x, z};
				case SOUTH -> new int[]{z, x};
				default -> new int[]{-z, x};
			};
		}

		public boolean near(int x, int z, int margin) {
			return x >= x0 - margin && x <= x1 + margin && z >= z0 - margin && z <= z1 + margin;
		}

		/** Inside the quay (land part and apron). */
		public boolean onQuay(int u, int v) {
			return u >= back && u <= face && Math.abs(v - mid) <= HALF;
		}

		/** On a pier deck: which (0, 1), or -1. */
		public int pier(int u, int v) {
			if (u <= face || u > face + PIER_LEN) {
				return -1;
			}
			for (int k = 0; k < piers.length; k++) {
				if (Math.abs(v - piers[k]) <= 2) {
					return k;
				}
			}
			return -1;
		}

		/**
		 * 1.33: a spot on open water straight out from the port, up to {@code out} blocks past the pier heads (as far as
		 * water deep enough for a warship goes), {@code dv} across; y = the water's surface.
		 */
		public BlockPos offshore(Cities.Terrain t, int out, int dv) {
			int u0 = face + PIER_LEN + 12;
			int best = u0;
			for (int u = u0; u <= u0 + out; u += 8) {
				int wet = 0;
				for (int k = -12; k <= 12; k += 12) {
					int[] w = world(u, mid + dv + k);
					if (t.top(w[0], w[1]) < sea_ - 3) {
						wet++;
					}
				}
				if (wet < 3) {
					break;
				}
				best = u;
			}
			int[] w = world(best, mid + dv);
			return new BlockPos(w[0], sea_, w[1]);
		}

		/** How far outside the quay's land part (for blending the ground), 0 inside. */
		public int outLand(int u, int v) {
			int du = Math.max(0, Math.max(back - u, u - face));
			int dv = Math.max(0, Math.abs(v - mid) - HALF);
			return Math.max(du, dv);
		}

		/** The ship, crane, containers, lighthouse and lamps, as {x, y, z, palette index} grouped by chunk. */
		Map<Long, List<long[]>> blocks() {
			Map<Long, List<long[]>> b = blocks;
			if (b == null) {
				synchronized (this) {
					b = blocks;
					if (b == null) {
						b = new Build(this).run();
						blocks = b;
					}
				}
			}
			return b;
		}

		BlockState state(long index) {
			return palette.get((int) index);
		}
	}

	private Ports() {
	}

	/** 1.33: the port of a town (planned cities only; null inland, or when its city is not planned yet). */
	@Nullable
	public static Port of(net.minecraft.server.level.ServerLevel level, Settlement s) {
		if (!s.isCity()) {
			return null;
		}
		Cities.City c = Cities.plannedCityAt(level.getSeed(), s.center.getX(), s.center.getZ(), 400);
		return c == null ? null : c.port(level.getSeed(), Cities.terrain(level));
	}

	/** The town's port, or null if no water is near (or the world has the classic towns). */
	@Nullable
	static Port plan(long seed, Cities.Terrain t, Cities.City c) {
		if (c.style == CityStyle.CLASSIC) {
			return null;
		}
		int sea = t.sea();
		// Most towns are inland: a quick look round first (a ring of samples), the careful search only if there is water.
		boolean water = false;
		for (int k = 0; k < 16 && !water; k++) {
			double a = k * Math.PI / 8;
			for (int r = c.half() + 40; r <= c.half() + 200; r += 60) {
				if (t.top(c.x + (int) (Math.cos(a) * r), c.z + (int) (Math.sin(a) * r)) < sea) {
					water = true;
					break;
				}
			}
		}
		if (!water) {
			return null;
		}
		Port best = null;
		double bestScore = Double.MAX_VALUE;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (int off = -60; off <= 60; off += 30) {
				Port probe = new Port(c, dir, 0, 0, 0, sea, 0);
				int[] cl = probe.local(c.x, c.z);
				int v0 = cl[1] + off;
				// The shore: walking out from the middle of the town, the first spot where most of three samples are water.
				int shore = Integer.MIN_VALUE;
				for (int u = cl[0] + 30; u <= cl[0] + c.half() + 200; u += 6) {
					int wet = 0;
					for (int dv = -16; dv <= 16; dv += 16) {
						int[] w = probe.world(u, v0 + dv);
						if (t.top(w[0], w[1]) < sea) {
							wet++;
						}
					}
					if (wet >= 2) {
						shore = u;
						break;
					}
				}
				if (shore == Integer.MIN_VALUE) {
					continue;
				}
				// Open water beyond, deep enough for the ship.
				boolean open = true;
				for (int du : new int[]{8, 20, 40}) {
					for (int dv = -HALF; dv <= HALF; dv += HALF) {
						int[] w = probe.world(shore + du, v0 + dv);
						if (t.top(w[0], w[1]) >= sea) {
							open = false;
						}
					}
				}
				int[] deep = probe.world(shore + 14, v0 - 22);
				int floor = t.floor(deep[0], deep[1]);
				if (!open || sea - floor < 2) {
					continue;
				}
				// The land part: outside the town, not up a cliff.
				double score = 0;
				boolean ok = true;
				for (int du = -LAND; du <= 0; du += 10) {
					for (int dv = -HALF; dv <= HALF; dv += 15) {
						int[] w = probe.world(shore + du, v0 + dv);
						if (c.outside(w[0], w[1]) < 8) {
							ok = false;
						}
						int y = t.top(w[0], w[1]);
						if (y > sea + 12) {
							ok = false;
						}
						score += Math.abs(y - (sea + 1));
					}
				}
				int[] w = probe.world(shore - LAND, v0);
				score += c.outside(w[0], w[1]) * 2;
				if (!ok || c.outside(w[0], w[1]) > 140) {
					continue;
				}
				if (score < bestScore) {
					bestScore = score;
					best = new Port(c, dir, shore, v0, sea + 1, sea, floor);
				}
			}
		}
		if (best == null) {
			return null;
		}
		Random r = new Random(c.seed ^ 0x9047_5EAL);
		// Two warehouses at the back, their doors onto the quay.
		Direction facing = best.sea.getOpposite();
		int k = 0;
		for (int dv : new int[]{-14, 14}) {
			int[] o = best.world(best.back + 13, best.mid + dv);
			Building b = new Building(k++, BuildingType.WAREHOUSE, new BlockPos(o[0], best.y, o[1]), facing, true);
			b.variant = r.nextInt(97);
			b.style = c.style.ordinal();
			best.buildings.add(b);
		}
		best.access = access(t, c, best);
		return best;
	}

	/** A straight road from the back of the port into the town. */
	@Nullable
	private static Cities.Road access(Cities.Terrain t, Cities.City c, Port p) {
		int[] gate = p.world(p.back - 1, p.mid);
		int u = p.back - 2;
		int[] at = gate;
		while (u > p.back - 200) {
			at = p.world(u, p.mid);
			if (c.outside(at[0], at[1]) == 0) {
				break;
			}
			u -= 2;
		}
		double len = Math.hypot(at[0] - gate[0], at[1] - gate[1]);
		int steps = Math.max(2, (int) Math.ceil(len / Cities.Road.STEP) + 1);
		float[] hs = new float[steps];
		for (int i = 0; i < steps; i++) {
			double f = (double) i / (steps - 1);
			hs[i] = (float) (p.y + (c.base - p.y) * f);
		}
		return new Cities.Road(new float[]{gate[0], at[0]}, new float[]{gate[1], at[1]}, hs, 3, false, false, 3);
	}

	/** Works out the port's structures once. */
	private static final class Build {
		final Port p;
		final Map<Long, List<long[]>> out = new HashMap<>();
		final Map<BlockState, Integer> index = new HashMap<>();
		final Random r;

		Build(Port p) {
			this.p = p;
			this.r = new Random(p.city.seed ^ 0x5417_0F1L);
		}

		void put(int u, int y, int v, BlockState s) {
			int[] w = p.world(u, v);
			Integer i = index.get(s);
			if (i == null) {
				i = p.palette.size();
				p.palette.add(s);
				index.put(s, i);
			}
			out.computeIfAbsent(ChunkPos.pack(w[0] >> 4, w[1] >> 4), k -> new ArrayList<>()).add(new long[]{w[0], y, w[1], i});
		}

		BlockState c(DyeColor d) {
			return Blocks.CONCRETE.pick(d).defaultBlockState();
		}

		Map<Long, List<long[]>> run() {
			int y = p.y;
			crane(p.piers[0]);
			crane(p.piers[1]);
			containers(y);
			ship(p.piers[0] - 3 - 10, p.piers[0] - 3);
			lighthouse(p.face + PIER_LEN, p.piers[1]);
			// Lamps along the apron and the piers, bollards on the edge.
			for (int v = p.mid - HALF + 2; v <= p.mid + HALF - 2; v += 8) {
				lamp(p.face - 3, v);
			}
			for (int v = p.mid - HALF; v <= p.mid + HALF; v += 5) {
				if (p.pier(p.face + 1, v) < 0) {
					put(p.face, y + 1, v, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
				}
			}
			for (int pv : p.piers) {
				for (int u = p.face + 4; u < p.face + PIER_LEN; u += 6) {
					put(u, y + 1, pv - 2, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
					put(u, y + 1, pv + 2, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
				}
				lamp(p.face + PIER_LEN / 2, pv + 2);
			}
			return out;
		}

		void lamp(int u, int v) {
			for (int k = 1; k <= 5; k++) {
				put(u, p.y + k, v, Blocks.IRON_BARS.defaultBlockState());
			}
			put(u, p.y + 6, v, Blocks.SEA_LANTERN.defaultBlockState());
			put(u, p.y + 7, v, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		}

		/** A ship-to-shore crane at the root of a pier: four legs, a portal, a long boom over the water, the cab. */
		void crane(int pv) {
			int y = p.y;
			BlockState leg = c(DyeColor.YELLOW);
			BlockState beam = c(DyeColor.ORANGE);
			int ul = p.face - 8;
			int uw = p.face - 1;
			for (int k = 1; k <= 13; k++) {
				for (int u : new int[]{ul, uw}) {
					put(u, y + k, pv - 4, leg);
					put(u, y + k, pv + 4, leg);
				}
			}
			for (int v = pv - 4; v <= pv + 4; v++) {
				put(ul, y + 14, v, beam);
				put(uw, y + 14, v, beam);
			}
			// The cross braces of the portal.
			for (int k = 0; k < 7; k++) {
				put(ul + k, y + 7 + k / 2, pv - 4, Blocks.IRON_BARS.defaultBlockState());
				put(ul + k, y + 7 + k / 2, pv + 4, Blocks.IRON_BARS.defaultBlockState());
			}
			// The boom: two girders from behind the land legs far out over the water, the A-frame over it.
			for (int u = ul - 6; u <= p.face + 22; u++) {
				put(u, y + 15, pv - 2, beam);
				put(u, y + 15, pv + 2, beam);
				if (Math.floorMod(u, 3) == 0) {
					for (int v = pv - 1; v <= pv + 1; v++) {
						put(u, y + 15, v, Blocks.IRON_BARS.defaultBlockState());
					}
				}
			}
			for (int k = 0; k <= 5; k++) {
				put(ul + 1, y + 16 + k, pv - 2, leg);
				put(ul + 1, y + 16 + k, pv + 2, leg);
			}
			for (int v = pv - 2; v <= pv + 2; v++) {
				put(ul + 1, y + 22, v, beam);
			}
			// The stays from the A-frame's top down to the boom's tip.
			int tip = p.face + 22;
			for (int u = ul + 2; u < tip; u++) {
				int yy = y + 21 - (u - ul - 2) * 6 / Math.max(1, tip - ul - 2);
				put(u, yy, pv, Blocks.IRON_CHAIN.defaultBlockState());
			}
			// The cab and the trolley above the pier, the spreader hanging on its cables.
			for (int u = p.face + 2; u <= p.face + 4; u++) {
				for (int v = pv - 1; v <= pv + 1; v++) {
					put(u, y + 14, v, u == p.face + 3 && v == pv ? Blocks.GLASS.defaultBlockState() : c(DyeColor.WHITE));
				}
			}
			for (int k = 6; k <= 13; k++) {
				put(p.face + 9, y + k, pv, Blocks.IRON_CHAIN.defaultBlockState());
			}
			for (int v = pv - 2; v <= pv + 2; v++) {
				put(p.face + 9, y + 5, v, c(DyeColor.YELLOW));
			}
		}

		/** Stacks of containers on the quay, in rows, two or three high. */
		void containers(int y) {
			DyeColor[] colors = {DyeColor.RED, DyeColor.BLUE, DyeColor.ORANGE, DyeColor.GREEN, DyeColor.GRAY, DyeColor.CYAN, DyeColor.BROWN,
					DyeColor.WHITE};
			for (int row = 0; row < 3; row++) {
				int u0 = p.back + 16 + row * 4;
				for (int v0 = p.mid - 8; v0 <= p.mid + 6; v0 += 3) {
					int h = 1 + r.nextInt(3);
					for (int level = 0; level < h; level++) {
						DyeColor col = colors[r.nextInt(colors.length)];
						box(u0, y + 1 + level * 2, v0, 5, 2, 2, col);
					}
				}
			}
		}

		/** A container: {@code lu} long (u), {@code h} high, {@code lv} wide, ribbed sides. */
		void box(int u0, int y0, int v0, int lu, int h, int lv, DyeColor col) {
			BlockState side = Blocks.DYED_TERRACOTTA.pick(col).defaultBlockState();
			BlockState rib = c(col);
			for (int u = u0; u < u0 + lu; u++) {
				for (int yy = y0; yy < y0 + h; yy++) {
					for (int v = v0; v < v0 + lv; v++) {
						put(u, yy, v, (u - u0) % 2 == 0 ? rib : side);
					}
				}
			}
		}

		/** A cargo ship moored alongside: its hull from v0 to v1 across, its bow out to sea; the bridge at the stern. */
		void ship(int v0, int v1) {
			int sea = p.sea_;
			int bottom = Math.max(p.floor + 1, sea - 4);
			int deck = sea + 2;
			int u0 = p.face + 3;
			int u1 = p.face + PIER_LEN + 2;
			int vm = (v0 + v1) / 2;
			int half = (v1 - v0) / 2;
			BlockState hull = c(StyleKit.pick((int) (p.city.seed & 7), DyeColor.BLUE, DyeColor.RED, DyeColor.BLACK, DyeColor.GREEN));
			BlockState bottomPaint = c(DyeColor.RED);
			BlockState white = c(DyeColor.WHITE);
			for (int u = u0; u <= u1; u++) {
				// Narrowing at the bow (the far end) and a little at the stern.
				int fromBow = u1 - u;
				int w = fromBow < 6 ? Math.max(0, half - (6 - fromBow)) : u - u0 < 2 ? half - 1 : half;
				for (int v = vm - w; v <= vm + w; v++) {
					boolean edge = Math.abs(v - vm) == w || u == u0 || u == u1;
					for (int yy = bottom; yy <= deck; yy++) {
						if (edge || yy == bottom) {
							put(u, yy, v, yy < sea - 1 ? bottomPaint : hull);
						} else if (yy == deck) {
							put(u, yy, v, Blocks.SMOOTH_STONE.defaultBlockState());
						} else {
							put(u, yy, v, Blocks.AIR.defaultBlockState());
						}
					}
					if (edge) {
						put(u, deck + 1, v, Blocks.IRON_BARS.defaultBlockState());
					}
				}
			}
			// The bridge: a white block at the stern, windows all round its top floor, the funnel behind.
			for (int yy = deck + 1; yy <= deck + 9; yy++) {
				for (int u = u0 + 1; u <= u0 + 5; u++) {
					for (int v = vm - half + 1; v <= vm + half - 1; v++) {
						boolean shell = u == u0 + 1 || u == u0 + 5 || Math.abs(v - vm) == half - 1;
						if (!shell) {
							continue;
						}
						put(u, yy, v, yy == deck + 8 ? Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState()
								: yy % 3 == 0 && u == u0 + 5 ? Blocks.GLASS_PANE.defaultBlockState() : white);
					}
				}
			}
			for (int u = u0; u <= u0 + 6; u++) {
				for (int v = vm - half; v <= vm + half; v++) {
					put(u, deck + 10, v, white);
				}
			}
			for (int yy = deck + 10; yy <= deck + 13; yy++) {
				put(u0 + 2, yy, vm, c(DyeColor.BLACK));
				put(u0 + 2, yy, vm + 1, c(DyeColor.BLACK));
			}
			put(u0 + 2, deck + 11, vm, c(DyeColor.RED));
			put(u0 + 5, deck + 11, vm - half + 1, Blocks.SEA_LANTERN.defaultBlockState());
			// Containers on deck, two high, between the bridge and the bow.
			DyeColor[] colors = {DyeColor.RED, DyeColor.BLUE, DyeColor.ORANGE, DyeColor.GREEN, DyeColor.WHITE, DyeColor.GRAY};
			for (int u = u0 + 8; u + 5 <= u1 - 7; u += 6) {
				for (int v = vm - half + 1; v + 2 <= vm + half; v += 2) {
					int h = 1 + r.nextInt(2);
					for (int level = 0; level < h; level++) {
						box(u, deck + 1 + level * 2, v, 5, 2, 2, colors[r.nextInt(colors.length)]);
					}
				}
			}
			// Mooring lines to the pier.
			put(u0 + 3, deck + 1, v1, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, p.sea.getAxis() == Direction.Axis.X
					? Direction.Axis.Z : Direction.Axis.X));
			put(u1 - 4, deck + 1, v1, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, p.sea.getAxis() == Direction.Axis.X
					? Direction.Axis.Z : Direction.Axis.X));
		}

		/** A lighthouse: a striped round-ish tower with a lantern room and a beacon. */
		void lighthouse(int u0, int v0) {
			int y = p.y;
			for (int k = 1; k <= 14; k++) {
				BlockState s = c((k / 3) % 2 == 0 ? DyeColor.WHITE : DyeColor.RED);
				for (int du = -1; du <= 1; du++) {
					for (int dv = -1; dv <= 1; dv++) {
						if (du != 0 || dv != 0) {
							put(u0 + du, y + k, v0 + dv, s);
						}
					}
				}
			}
			for (int du = -2; du <= 2; du++) {
				for (int dv = -2; dv <= 2; dv++) {
					put(u0 + du, y + 15, v0 + dv, c(DyeColor.GRAY));
				}
			}
			for (int k = 16; k <= 17; k++) {
				for (int du = -1; du <= 1; du++) {
					for (int dv = -1; dv <= 1; dv++) {
						put(u0 + du, y + k, v0 + dv, du == 0 && dv == 0 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.GLASS.defaultBlockState());
					}
				}
			}
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					put(u0 + du, y + 18, v0 + dv, c(DyeColor.RED));
				}
			}
			put(u0, y + 19, v0, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		}
	}
}
