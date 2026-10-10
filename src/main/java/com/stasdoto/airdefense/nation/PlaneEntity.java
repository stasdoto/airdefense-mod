package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 1.40: an airliner at an airport - landing, taxiing to its stand, parked, pushed back, taxiing out, taking off. Like the
 * trains, only the player's own client knows it (client.nation.AirTraffic makes it, gives it its way and moves it), so
 * it costs the server nothing.
 */
public class PlaneEntity extends Entity {
	/** One point of its way: where (world x, height, z), how fast to be going there, backwards (pushed), how long to wait. */
	public record Point(double x, double y, double z, double speed, boolean back, int hold) {
	}

	public String model = "airliner";
	/** From its origin to its nose (it parks with the nose at the stand's mark). */
	public double nose = 18.8;
	public final List<Point> way = new ArrayList<>();
	private final List<Double> cum = new ArrayList<>();
	public double s;
	public double speed;
	public int next = 1;
	public int holding;
	/** Done with its way (parked, or gone out of sight). */
	public boolean arrived;
	/** Its pose now and a tick ago: x, y, z, yaw, pitch. */
	public double[] pose = new double[5];
	public double[] poseO = new double[5];
	/** The airport (its key), the stand it is at or going to, what it is doing: 0 landing, 1 parked, 2 leaving. */
	public long airport;
	public int stand = -1;
	public int phase;
	public long parkedAt;
	public int lastSound = -1000;

	public PlaneEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** A new way from where it is now. */
	public void go(List<Point> points) {
		way.clear();
		cum.clear();
		way.addAll(points);
		double c = 0;
		cum.add(0.0);
		for (int i = 1; i < way.size(); i++) {
			Point a = way.get(i - 1);
			Point b = way.get(i);
			c += Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y) + (b.z - a.z) * (b.z - a.z));
			cum.add(c);
		}
		s = 0;
		next = 1;
		holding = 0;
		arrived = false;
		speed = way.isEmpty() ? 0 : way.getFirst().speed;
	}

	private double[] at(double d) {
		int i = 1;
		while (i < cum.size() - 1 && cum.get(i) < d) {
			i++;
		}
		Point a = way.get(i - 1);
		Point b = way.get(i);
		double len = cum.get(i) - cum.get(i - 1);
		double f = len < 1e-6 ? 1 : Math.max(0, Math.min(1, (d - cum.get(i - 1)) / len));
		return new double[]{a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f, b.back ? 1 : 0};
	}

	/** One tick along its way: speeding up or braking for the points ahead (in time to be at their speeds), waiting. */
	public void step() {
		poseO = pose.clone();
		if (way.size() < 2 || arrived) {
			return;
		}
		if (holding > 0) {
			holding--;
			speed = 0;
			return;
		}
		double total = cum.getLast();
		// The fastest it may go now and still slow down to each point's speed in time.
		double ground = pose[1] - way.getLast().y < 2 ? 0.012 : 0.03;
		double limit = Double.MAX_VALUE;
		for (int i = next; i < way.size(); i++) {
			double dist = cum.get(i) - s;
			if (dist > 400) {
				break;
			}
			Point p = way.get(i);
			double end = p.hold > 0 || i == way.size() - 1 ? 0 : p.speed;
			limit = Math.min(limit, Math.sqrt(end * end + 2 * ground * Math.max(0, dist - 0.3)));
		}
		Point target = way.get(next);
		// Never quite stopping short of a point it is to stop at (it creeps the last bit).
		double want = Math.max(0.03, Math.min(limit, target.speed));
		speed = speed < want ? Math.min(want, speed + (pose[1] - way.getLast().y > 2 ? 0.03 : 0.012)) : want;
		s += speed;
		while (next < way.size() && s >= cum.get(next) - 0.1) {
			Point p = way.get(next);
			if (p.hold > 0) {
				s = cum.get(next);
				holding = p.hold;
				speed = 0;
			}
			next++;
		}
		if (next >= way.size() || s >= total - 0.1) {
			s = total;
			arrived = true;
		}
		pose();
	}

	/** Where it is and which way it looks, from its place along the way. */
	public void pose() {
		if (way.size() < 2) {
			return;
		}
		double[] p = at(s);
		double[] a = at(Math.min(cum.getLast(), s + 6));
		double[] b = at(Math.max(0, s - 6));
		double dx = a[0] - b[0];
		double dy = a[1] - b[1];
		double dz = a[2] - b[2];
		boolean back = p[3] > 0;
		double yaw = dx * dx + dz * dz < 1e-6 ? pose[3] : Math.toDegrees(Math.atan2(-dx, dz)) + (back ? 180 : 0);
		double climb = Math.toDegrees(Math.atan2(dy, Math.max(1e-6, Math.sqrt(dx * dx + dz * dz))));
		double pitch = back ? 0 : p[1] - way.getLast().y > 0.5 || climb > 1 ? Math.max(2, climb + 3) : 0;
		if (climb < -1) {
			// On the approach: nose a little up, coming down the glide path.
			pitch = 2.5;
		}
		pose = new double[]{p[0], p[1], p[2], yaw, pitch};
		setPos(p[0], p[1], p[2]);
	}

	/** Stands still here (parked). */
	public void place(double x, double y, double z, double yaw) {
		way.clear();
		cum.clear();
		pose = new double[]{x, y, z, yaw, 0};
		poseO = pose.clone();
		arrived = true;
		speed = 0;
		setPos(x, y, z);
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			discard();
			return;
		}
		// Moved by the client's air traffic (also where its chunk does not tick: on the approach, far out).
		tickCount++;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
