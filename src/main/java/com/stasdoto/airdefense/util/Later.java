package com.stasdoto.airdefense.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Work that needs a piece of the world loaded, done once it is (1.25.1). Loading a chunk on the spot
 * ({@code level.getChunk}) stops the whole server until it is read - or generated - from disk: a freeze. Here the
 * chunk is asked for (a ticket: it loads in the background) and the work waits a few ticks for it.
 */
public final class Later {
	private record Job(ServerLevel level, BlockPos at, Consumer<ServerLevel> work, long giveUpAt) {
	}

	private static final List<Job> JOBS = new ArrayList<>();
	/** For the tests: jobs done, jobs given up. */
	public static int done;
	public static int givenUp;

	/** Jobs waiting for their ground. */
	public static int pending() {
		return JOBS.size();
	}

	private Later() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long t0 = System.nanoTime();
			tick();
			Perf.add(Perf.LATER, System.nanoTime() - t0);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> JOBS.clear());
	}

	/** Does {@code work} as soon as the chunk at {@code at} is loaded (now, if it is), within {@code maxTicks}. */
	public static void whenLoaded(ServerLevel level, BlockPos at, int maxTicks, Consumer<ServerLevel> work) {
		if (level.isLoaded(at)) {
			work.accept(level);
			done++;
			return;
		}
		level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(at), 2);
		JOBS.add(new Job(level, at.immutable(), work, level.getGameTime() + maxTicks));
	}

	private static void tick() {
		if (JOBS.isEmpty()) {
			return;
		}
		List<Job> ready = new ArrayList<>();
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
			Job j = it.next();
			if (j.level.isLoaded(j.at)) {
				it.remove();
				ready.add(j);
			} else if (j.level.getGameTime() > j.giveUpAt) {
				it.remove();
				givenUp++;
			} else if (j.level.getGameTime() % 20 == 0) {
				// The ticket runs out after a while: keep asking.
				j.level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(j.at), 2);
			}
		}
		for (Job j : ready) {
			long t0 = System.nanoTime();
			j.work.accept(j.level);
			Perf.over("later job " + j.work.getClass().getName() + " at " + j.at.toShortString(), t0);
			done++;
		}
	}
}
