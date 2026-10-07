package com.stasdoto.airdefense.drone;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.radar.RadarNetwork;
import com.stasdoto.airdefense.registry.ModSounds;

/**
 * Massed raids: a wave of Shaheds (with Gerbera decoys among them), a few cruise missiles and maybe a ballistic
 * missile, launched one after another from 450-550 blocks away on one side, all aimed at one area. Used by enemy
 * countries at war with a player (at night), by the tablet's "mass raid" (the player's own), and by /ad raid.
 * When the radars see the wave coming - or, without radar, when the drones are already close - every player nearby
 * gets the air raid alert and the sirens howl.
 */
public final class Raids {
	private record Shot(long at, MissileType type, Vec3 from, Vec3 aim, FlightPlan plan, boolean left) {
	}

	private static final class Raid {
		final ResourceKey<Level> dimension;
		final Vec3 center;
		final String direction;
		final List<Shot> shots = new ArrayList<>();
		final List<MissileEntity> flying = new ArrayList<>();
		final boolean enemy;
		boolean alerted;
		int total;
		long started;

		Raid(ResourceKey<Level> dimension, Vec3 center, String direction, boolean enemy) {
			this.dimension = dimension;
			this.center = center;
			this.direction = direction;
			this.enemy = enemy;
		}
	}

	private static final List<Raid> RAIDS = new ArrayList<>();
	/** For the tests: missiles launched by raids, and alerts given. */
	public static int launched;
	public static int alerts;

	private Raids() {
	}

	public static boolean activeNear(ServerLevel level, Vec3 at, double radius) {
		for (Raid r : RAIDS) {
			if (r.dimension == level.dimension() && r.center.distanceToSqr(at) < radius * radius) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Starts a raid on {@code center} from the direction {@code bearing} (radians, the way from the target towards the
	 * launchers: 0 = east, PI/2 = south). {@code enemy} = it is against the players there (alerts and sirens).
	 */
	public static int start(ServerLevel level, Vec3 center, double bearing, int drones, int cruise, int ballistic, boolean enemy) {
		Random r = new Random();
		Raid raid = new Raid(level.dimension(), center, compass(bearing), enemy);
		long now = level.getGameTime();
		long t = now + 20;
		for (int i = 0; i < drones; i++) {
			double a = bearing + r.nextGaussian() * 0.22;
			double d = 450 + r.nextDouble() * 100;
			Vec3 from = new Vec3(center.x + Math.cos(a) * d, center.y + 40 + r.nextInt(20), center.z + Math.sin(a) * d);
			MissileType type = r.nextFloat() < 0.3f ? MissileType.GERBERA : MissileType.SHAHED;
			int maneuver = r.nextFloat() < 0.25f ? FlightPlan.FLANK : r.nextFloat() < 0.15f ? FlightPlan.LOW : FlightPlan.WEAVE;
			FlightPlan plan = new FlightPlan(40 + r.nextInt(40), 90 + r.nextInt(11), maneuver, 0, false);
			raid.shots.add(new Shot(t, type, from, aim(center, r, 40), plan, r.nextBoolean()));
			t += 6 + r.nextInt(12);
		}
		for (int i = 0; i < cruise; i++) {
			double a = bearing + r.nextGaussian() * 0.15;
			double d = 520 + r.nextDouble() * 60;
			Vec3 from = new Vec3(center.x + Math.cos(a) * d, center.y + 30, center.z + Math.sin(a) * d);
			FlightPlan plan = new FlightPlan(35 + r.nextInt(25), 100, r.nextBoolean() ? FlightPlan.LOW : FlightPlan.STRAIGHT, 0, false);
			raid.shots.add(new Shot(now + 60 + r.nextInt(200), MissileType.KALIBR, from, aim(center, r, 25), plan, r.nextBoolean()));
		}
		for (int i = 0; i < ballistic; i++) {
			double a = bearing + r.nextGaussian() * 0.1;
			double d = 600;
			Vec3 from = new Vec3(center.x + Math.cos(a) * d, center.y + 30, center.z + Math.sin(a) * d);
			raid.shots.add(new Shot(now + 300 + r.nextInt(200), MissileType.ISKANDER, from, aim(center, r, 20), FlightPlan.DEFAULT, false));
		}
		raid.total = raid.shots.size();
		raid.started = now;
		RAIDS.add(raid);
		AirDefense.LOGGER.info("[airdefense] raid on {} from the {}: {} drones, {} cruise, {} ballistic", BlockPos.containing(center),
				raid.direction, drones, cruise, ballistic);
		return raid.total;
	}

	private static Vec3 aim(Vec3 center, Random r, double spread) {
		double a = r.nextDouble() * Math.PI * 2;
		double d = Math.sqrt(r.nextDouble()) * spread;
		return new Vec3(center.x + Math.cos(a) * d, center.y, center.z + Math.sin(a) * d);
	}

	private static String compass(double bearing) {
		// bearing: 0 = +x (east), PI/2 = +z (south).
		String[] names = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
		int i = (int) Math.round(bearing / (Math.PI / 4));
		return names[Math.floorMod(i, 8)];
	}

	public static void tick(MinecraftServer server) {
		if (RAIDS.isEmpty()) {
			return;
		}
		for (Iterator<Raid> it = RAIDS.iterator(); it.hasNext(); ) {
			Raid raid = it.next();
			ServerLevel level = server.getLevel(raid.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			for (Iterator<Shot> s = raid.shots.iterator(); s.hasNext(); ) {
				Shot shot = s.next();
				BlockPos at = BlockPos.containing(shot.from);
				if (shot.at - now <= 30) {
					// Load the launch area a moment before.
					level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(at), 2);
				}
				if (shot.at > now) {
					continue;
				}
				if (!level.isLoaded(at)) {
					if (now - shot.at > 100) {
						s.remove();
					}
					continue;
				}
				s.remove();
				Vec3 flat = shot.aim.subtract(shot.from);
				MissileEntity m = MissileEntity.launchStrike(level, shot.type, shot.from, shot.aim, new Vec3(flat.x, 0, flat.z).normalize());
				if (shot.type.kind == MissileType.Kind.DRONE || shot.type.kind == MissileType.Kind.CRUISE) {
					m.applyPlan(shot.plan, shot.left);
				}
				raid.flying.add(m);
				launched++;
			}
			raid.flying.removeIf(m -> !m.isAlive() || m.isRemoved());
			if (raid.enemy && !raid.alerted && now % 10 == 0) {
				checkAlert(level, raid);
			}
			if (raid.alerted && now % 200 == 0 && !raid.flying.isEmpty()) {
				// Keep the alert on while the raid is still in the air (the all clear sounds when it is over).
				com.stasdoto.airdefense.siren.Sirens.autoAlert(level, raid.center, 400);
			}
			if (raid.shots.isEmpty() && raid.flying.isEmpty()) {
				it.remove();
			}
		}
	}

	/** The radars see the wave, or (without radar) the first drones are already overhead: air raid alert. */
	private static void checkAlert(ServerLevel level, Raid raid) {
		boolean seen = false;
		boolean close = false;
		for (MissileEntity m : raid.flying) {
			if (RadarNetwork.coverage(level, m.position()) != null) {
				seen = true;
			}
			if (m.position().distanceToSqr(raid.center) < 170 * 170) {
				close = true;
			}
		}
		if (!seen && !close) {
			return;
		}
		raid.alerted = true;
		alerts++;
		com.stasdoto.airdefense.siren.Sirens.autoAlert(level, raid.center, 400);
		Component title = Component.translatable("message.airdefense.raid.title");
		Component sub = Component.translatable(seen ? "message.airdefense.raid.radar" : "message.airdefense.raid.late",
				Component.translatable("message.airdefense.dir." + raid.direction), raid.total);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(raid.center) < 700 * 700) {
				p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
				p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
				p.connection.send(new ClientboundSetTitleTextPacket(title));
				p.sendSystemMessage(Component.empty().append(title).append(" ").append(sub));
				level.playSound(null, p.getX(), p.getY() + 8, p.getZ(), ModSounds.SIREN, SoundSource.HOSTILE, 4.0f, 1.0f);
			}
		}
		level.playSound(null, raid.center.x, raid.center.y + 10, raid.center.z, ModSounds.SIREN, SoundSource.HOSTILE, 6.0f, 0.95f);
	}

	/** For /ad raid and the tests: raid on the player's position from a random side. */
	public static int onPlayer(ServerPlayer player, int drones, @Nullable Double bearing) {
		double b = bearing != null ? bearing : new Random().nextDouble() * Math.PI * 2;
		return start(player.level(), player.position(), b, drones, Math.max(1, drones / 8), drones >= 16 ? 1 : 0, true);
	}
}
