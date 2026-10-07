package com.stasdoto.airdefense.siren;

import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;

/** The client listens here for sounding sirens (set by the client code; empty on a dedicated server). */
public final class SirenSounds {
	public static BiConsumer<BlockPos, SirenBlock.Signal> listener = (pos, signal) -> {
	};

	private SirenSounds() {
	}

	static void report(BlockPos pos, SirenBlock.Signal signal) {
		listener.accept(pos, signal);
	}
}
