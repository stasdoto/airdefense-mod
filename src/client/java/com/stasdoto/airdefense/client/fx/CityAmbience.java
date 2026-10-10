package com.stasdoto.airdefense.client.fx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.client.map.AtlasClient;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * 1.36: the sounds of a town round the player - the hum of the traffic by day (quieter at night), a car going by now
 * and then, a horn, birds in the morning and by day, crickets and an owl at night, the town hall's bell striking the
 * hours (from 7 in the morning to 10 at night). All on the client, from the towns it already knows (the tablet's atlas);
 * looked at twice a second. Played as "ambient", so the game's "Ambient/Environment" volume turns them down.
 */
public final class CityAmbience {
	private static final RandomSource RANDOM = RandomSource.create();
	private static Loop traffic;
	private static Loop crickets;
	private static int car;
	private static int horn;
	private static int birds;
	private static int owl;
	private static long lastHour = -1;
	private static int strikesLeft;
	private static int strikeWait;
	private static Vec3 bellAt;
	private static int ticks;
	/** For the tests: one-shot sounds played so far. */
	public static int played;
	public static boolean inTown;

	private CityAmbience() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CityAmbience::tick);
	}

	/** The town (from the atlas) whose blocks cover (x, z), or null. */
	static AtlasClient.City townAt(double x, double z) {
		for (AtlasClient.City c : AtlasClient.CITIES) {
			int n = c.n();
			if (n == 0 || x < c.gx()[0] || z < c.gz()[0] || x >= c.gx()[n] || z >= c.gz()[n]) {
				continue;
			}
			int i = 0;
			while (i < n - 1 && x >= c.gx()[i + 1]) {
				i++;
			}
			int j = 0;
			while (j < n - 1 && z >= c.gz()[j + 1]) {
				j++;
			}
			if (c.cellOn(i, j)) {
				return c;
			}
		}
		return null;
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			stopLoops();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		if (strikesLeft > 0 && --strikeWait <= 0) {
			play(mc, ModSounds.TOWN_BELL, bellAt, 1.6f, 1.0f);
			strikesLeft--;
			strikeWait = 50;
		}
		if (++ticks % 10 != 0) {
			return;
		}
		Vec3 me = mc.player.position();
		AtlasClient.City town;
		try {
			town = townAt(me.x, me.z);
		} catch (RuntimeException e) {
			town = null;
		}
		// Towns the atlas does not cover (far out): told by what stands round the player - lit windows, street lamps, asphalt.
		inTown = town != null || ticks % 40 == 0 ? town != null || looksLikeTown(mc, me) : inTown;
		long time = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
		boolean night = time >= 13000 && time < 23000;
		boolean morning = time >= 23000 || time < 3000;
		// Under the ground or high in the air the town is not heard.
		boolean open = mc.level.canSeeSky(mc.player.blockPosition().above()) && me.y < 200;
		float trafficGain = inTown && open ? (night ? 0.12f : 0.35f) : 0f;
		float cricketGain = inTown && open && night ? 0.18f : 0f;
		traffic = loop(mc, traffic, ModSounds.CITY_TRAFFIC, trafficGain);
		crickets = loop(mc, crickets, ModSounds.CITY_CRICKETS, cricketGain);
		if (!inTown || !open) {
			return;
		}
		// Now and then: twice a second a chance each, counted down.
		if (--car <= 0) {
			car = (night ? 40 : 10) + RANDOM.nextInt(night ? 60 : 24);
			play(mc, ModSounds.CITY_CAR, around(me, 14, 30), night ? 0.5f : 0.8f, 0.9f + RANDOM.nextFloat() * 0.2f);
		}
		if (!night && --horn <= 0) {
			horn = 40 + RANDOM.nextInt(80);
			play(mc, ModSounds.CITY_HORN, around(me, 20, 40), 0.6f, 0.95f + RANDOM.nextFloat() * 0.1f);
		}
		if (!night && --birds <= 0) {
			birds = (morning ? 4 : 10) + RANDOM.nextInt(morning ? 8 : 20);
			play(mc, ModSounds.CITY_BIRDS, around(me, 6, 18).add(0, 4, 0), 0.6f, 0.9f + RANDOM.nextFloat() * 0.25f);
		}
		if (night && --owl <= 0) {
			owl = 60 + RANDOM.nextInt(120);
			play(mc, ModSounds.CITY_OWL, around(me, 30, 50).add(0, 8, 0), 0.7f, 0.9f + RANDOM.nextFloat() * 0.15f);
		}
		// The bell on the hour, from the town's middle (the town hall).
		long hour = (time / 1000 + 6) % 24;
		if (hour != lastHour) {
			if (lastHour >= 0 && hour >= 7 && hour <= 22 && time % 1000 < 200 && town != null) {
				strikesLeft = (int) (hour % 12 == 0 ? 12 : hour % 12);
				strikeWait = 1;
				bellAt = new Vec3(town.x(), me.y + 20, town.z());
			}
			lastHour = hour;
		}
	}

	private static final net.minecraft.world.level.block.Block ASPHALT = net.minecraft.world.level.block.Blocks.CONCRETE
			.pick(net.minecraft.world.item.DyeColor.GRAY);

	/** A look at two dozen spots round the player: street furniture, town windows, asphalt and pavement. */
	private static boolean looksLikeTown(Minecraft mc, Vec3 me) {
		int score = 0;
		net.minecraft.core.BlockPos.MutableBlockPos q = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (int i = 0; i < 24; i++) {
			double a = i * (Math.PI * 2 / 24) + RANDOM.nextDouble() * 0.2;
			double r = 6 + (i % 4) * 7;
			int x = (int) Math.floor(me.x + Math.cos(a) * r);
			int z = (int) Math.floor(me.z + Math.sin(a) * r);
			int top = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
			for (int y = top - 1; y >= top - 4; y--) {
				net.minecraft.world.level.block.Block b = mc.level.getBlockState(q.set(x, y, z)).getBlock();
				if (b instanceof com.stasdoto.airdefense.street.StreetBlock || b instanceof com.stasdoto.airdefense.street.StreetPoleBlock
						|| b instanceof com.stasdoto.airdefense.street.CityWindowBlock || b instanceof com.stasdoto.airdefense.street.CityGlassBlock) {
					score += 2;
				} else if (b == ASPHALT || b == net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE) {
					score++;
				}
			}
		}
		return score >= 8;
	}

	private static Vec3 around(Vec3 me, double r0, double r1) {
		double a = RANDOM.nextDouble() * Math.PI * 2;
		double r = r0 + RANDOM.nextDouble() * (r1 - r0);
		return me.add(Math.cos(a) * r, 0, Math.sin(a) * r);
	}

	private static void play(Minecraft mc, SoundEvent e, Vec3 at, float volume, float pitch) {
		mc.getSoundManager().play(new SimpleSoundInstance(e, SoundSource.AMBIENT, volume, pitch, RANDOM, at.x, at.y, at.z));
		played++;
	}

	private static Loop loop(Minecraft mc, Loop l, SoundEvent e, float gain) {
		if (gain <= 0) {
			if (l != null) {
				l.target = 0;
				if (l.isStopped()) {
					return null;
				}
			}
			return l;
		}
		if (l == null || l.isStopped()) {
			l = new Loop(e);
			mc.getSoundManager().play(l);
		}
		l.target = gain;
		return l;
	}

	private static void stopLoops() {
		if (traffic != null) {
			traffic.target = 0;
			traffic = null;
		}
		if (crickets != null) {
			crickets.target = 0;
			crickets = null;
		}
	}

	/** A layer that loops round the listener, its loudness easing to the target (and stopping once silent). */
	private static final class Loop extends AbstractTickableSoundInstance {
		float target;

		Loop(SoundEvent e) {
			super(e, SoundSource.AMBIENT, RandomSource.create());
			this.looping = true;
			this.delay = 0;
			this.volume = 0.001f;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public void tick() {
			volume += (target - volume) * 0.05f;
			if (target <= 0 && volume < 0.005f) {
				stop();
			}
		}
	}
}
