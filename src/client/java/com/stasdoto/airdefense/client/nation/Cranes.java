package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.nation.PropEntity;
import com.stasdoto.airdefense.registry.ModEntities;
import com.stasdoto.airdefense.street.StreetBlocks;

/**
 * 1.41: the tower cranes over the towns' building sites, on the player's client: one stands on each crane mark within
 * sight, its top turning now this way, now that (swinging a load across the site), resting a while between.
 */
public final class Cranes {
	private static final Map<Long, long[]> MARKS = new ConcurrentHashMap<>();
	private static final Map<Long, PropEntity> CRANES = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static int nextId = -5_000_000;
	private static ClientLevel lastLevel;
	private static int ticks;

	private Cranes() {
	}

	public static void init() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> index(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> MARKS.remove(chunk.getPos().pack()));
	}

	/** Positions of a block in a chunk (only its sections that may hold one are looked through). */
	static long[] find(LevelChunk chunk, Block block) {
		LevelChunkSection[] sections = chunk.getSections();
		List<Long> found = null;
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection s = sections[i];
			if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.is(block))) {
				continue;
			}
			int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (s.getBlockState(x, y, z).is(block)) {
							if (found == null) {
								found = new ArrayList<>();
							}
							found.add(BlockPos.asLong(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z));
						}
					}
				}
			}
		}
		return found == null ? null : found.stream().mapToLong(Long::longValue).toArray();
	}

	private static void index(LevelChunk chunk) {
		long[] f = find(chunk, StreetBlocks.CRANE_BASE);
		if (f != null) {
			MARKS.put(chunk.getPos().pack(), f);
		} else {
			MARKS.remove(chunk.getPos().pack());
		}
	}

	public static int count() {
		return CRANES.size();
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			CRANES.clear();
			if (lastLevel != null && level != null) {
				MARKS.clear();
			}
			lastLevel = level;
		}
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		for (PropEntity c : CRANES.values()) {
			c.hold();
			// Turning towards where it is to swing the load, then resting there a while.
			if (c.timer > 0) {
				c.timer--;
			} else {
				float d = net.minecraft.util.Mth.wrapDegrees((float) c.s - c.spin);
				if (Math.abs(d) < 0.4f) {
					c.timer = 100 + RANDOM.nextInt(300);
					c.s = c.spin + (RANDOM.nextBoolean() ? 1 : -1) * (40 + RANDOM.nextInt(140));
				} else {
					c.spin += Math.signum(d) * Math.min(Math.abs(d), 0.35f);
				}
			}
		}
		if (++ticks % 40 != 0) {
			return;
		}
		Vec3 me = mc.player.position();
		for (long[] list : MARKS.values()) {
			for (long l : list) {
				if (CRANES.containsKey(l)) {
					continue;
				}
				BlockPos p = BlockPos.of(l);
				if (Math.hypot(p.getX() - me.x, p.getZ() - me.z) > 260 || !level.getBlockState(p).is(StreetBlocks.CRANE_BASE)) {
					continue;
				}
				PropEntity c = new PropEntity(ModEntities.PROP, level);
				c.setId(nextId--);
				c.model = "tower_crane";
				c.spin = (float) Math.floorMod(l * 2654435761L, 360L);
				c.s = c.spin;
				c.move(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0, 0);
				c.poseO = c.pose;
				c.spinO = c.spin;
				c.setOldPosAndRot();
				level.addEntity(c);
				CRANES.put(l, c);
			}
		}
		for (var it = CRANES.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			PropEntity c = e.getValue();
			BlockPos p = BlockPos.of(e.getKey());
			if (Math.hypot(p.getX() - me.x, p.getZ() - me.z) > 300 || !level.hasChunkAt(p) || !level.getBlockState(p).is(StreetBlocks.CRANE_BASE)) {
				level.removeEntity(c.getId(), Entity.RemovalReason.DISCARDED);
				c.discard();
				it.remove();
			}
		}
	}
}
