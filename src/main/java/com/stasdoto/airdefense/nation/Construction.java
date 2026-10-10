package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.stasdoto.airdefense.street.StreetBlocks;

/**
 * 1.41: the towns are growing - in each city of a new world one or two of the tall buildings are still going up: a
 * bare concrete shell up to a part of its height (no windows yet, the rods sticking out of the top floor), scaffolding
 * round it, a tower crane over it (its mark at the foot of the shaft left open for the mast: the crane itself is the
 * player's client's, see client.nation.Cranes).
 */
public final class Construction {
	private static final BlockState SHELL = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	private static final BlockState REBAR = Blocks.IRON_BARS.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState SCAFFOLD = Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0)
			.setValue(ScaffoldingBlock.BOTTOM, false);

	private Construction() {
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return z ^ (z >>> 33);
	}

	/** Is this building of the city still going up? (One or two of its tall ones; only in worlds started since 1.41.) */
	public static boolean site(Cities.City c, Building b) {
		if (!Cities.growth) {
			return false;
		}
		int n = 0;
		for (Building o : c.buildings()) {
			if (!tall(o) || Math.floorMod(mix(c.seed ^ o.id * 0x9E37L), 7) != 0) {
				continue;
			}
			if (o == b) {
				return true;
			}
			if (++n >= 2) {
				return false;
			}
		}
		return false;
	}

	private static boolean tall(Building b) {
		return b.type == BuildingType.APARTMENTS || b.type == BuildingType.OFFICE || b.type == BuildingType.TOWER;
	}

	/** The building's blocks as a site: the shell up to part of its height, no windows, rods on top, scaffolding round it. */
	static List<Blueprints.Placement> shell(Building b, List<Blueprints.Placement> full) {
		int base = b.origin.getY();
		int top = base;
		int x0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z0 = Integer.MAX_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (Blueprints.Placement p : full) {
			if (!p.state().isAir() && p.pos().getY() > base) {
				top = Math.max(top, p.pos().getY());
				x0 = Math.min(x0, p.pos().getX());
				x1 = Math.max(x1, p.pos().getX());
				z0 = Math.min(z0, p.pos().getZ());
				z1 = Math.max(z1, p.pos().getZ());
			}
		}
		if (top <= base + 4) {
			return full;
		}
		double f = 0.35 + Math.floorMod(mix(b.id * 31L + b.origin.asLong()), 35) / 100.0;
		int cut = base + 3 + (int) ((top - base - 3) * f);
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		List<Blueprints.Placement> out = new ArrayList<>(full.size());
		for (Blueprints.Placement p : full) {
			BlockPos q = p.pos();
			BlockState s = p.state();
			int y = q.getY();
			boolean shaft = Math.abs(q.getX() - cx) <= 1 && Math.abs(q.getZ() - cz) <= 1 && y > base;
			if (y > cut || shaft || p.pair()) {
				// Not built yet (and kept clear: the land, trees); the shaft for the crane's mast.
				out.add(new Blueprints.Placement(q, AIR, null, null));
				continue;
			}
			if (s.isAir() || y <= base) {
				out.add(p);
				continue;
			}
			// Only the solid shell so far: no glass, no fittings, no furniture.
			boolean solid = s.isSolidRender() && !s.is(Blocks.GLASS) && !s.is(net.minecraft.tags.BlockTags.LEAVES);
			if (!solid) {
				out.add(new Blueprints.Placement(q, AIR, null, null));
			} else if (y == cut) {
				// The top floor being poured: rods sticking up from the walls' tops.
				out.add(new Blueprints.Placement(q, SHELL, null, null));
				if (((q.getX() + q.getZ()) & 1) == 0 && (q.getX() == x0 || q.getX() == x1 || q.getZ() == z0 || q.getZ() == z1)) {
					out.add(new Blueprints.Placement(q.above(), REBAR, null, null));
				}
			} else {
				out.add(new Blueprints.Placement(q, SHELL, null, null));
			}
		}
		// Scaffolding up the outside, a column every three blocks along the walls.
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
				if (!edge || Math.floorMod(x + z, 3) != 0) {
					continue;
				}
				for (int y = base + 1; y <= cut + 1; y++) {
					out.add(new Blueprints.Placement(new BlockPos(x, y, z), SCAFFOLD.setValue(ScaffoldingBlock.BOTTOM, y == base + 1), null, null));
				}
			}
		}
		// The crane's mark at the foot of the shaft.
		out.add(new Blueprints.Placement(new BlockPos(cx, base + 1, cz), StreetBlocks.CRANE_BASE.defaultBlockState(), null, null));
		return out;
	}
}
