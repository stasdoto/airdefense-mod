package com.stasdoto.airdefense.nation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.stasdoto.airdefense.street.StreetBlocks;
import com.stasdoto.airdefense.street.TrackBlock;

/**
 * 1.39: a train on a railway - a suburban electric train or a goods train. Like the passers-by, only the player's own
 * game knows it (made, run and taken away on the client: see client.nation.Trains), so the trains cost the server
 * nothing. It follows the track blocks themselves: ahead of it the way is traced block by block as it goes, behind it
 * forgotten. It slows for the end of the line, stops at the station, and an electric train then sets off back the other
 * way; a goods train waits and is gone.
 */
public class TrainEntity extends Entity {
	/** The cars: the model of each, its length, turned round or not (an electric train has a cab at both ends). */
	public final List<String> cars = new ArrayList<>();
	public final List<Float> lengths = new ArrayList<>();
	public final List<Boolean> flipped = new ArrayList<>();
	public boolean electric;
	/** The way: points along the rails (x, y, z), each a block of track; how far along it the front of the train is. */
	private final List<double[]> pts = new ArrayList<>();
	private final List<Double> cum = new ArrayList<>();
	private int lastMx;
	private int lastMz;
	private BlockPos lastCell;
	/** The way ahead ends: at a buffer stop (true), or where the land is not loaded (false, when {@link #open}). */
	private boolean ends;
	public boolean open = true;
	public double head;
	public double speed;
	public double top = 0.9;
	public int waiting;
	public int stuck;
	/** Stopped at the end of the line and done (a goods train): the client takes it away. */
	public boolean done;
	/** For the tests and the sounds: stops made, how far it has run. */
	public int stops;
	public double run;
	/** For the client's sounds: when it last hooted; just setting off from a station. */
	public int lastHoot = -1000;
	public boolean departing;
	/** Each car's pose now and a tick ago: x, y, z, yaw, pitch. */
	public double[][] pose = new double[0][];
	public double[][] poseO = new double[0][];
	public static java.util.function.Consumer<TrainEntity> clientTick;

	public TrainEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Gap between two cars (the couplers). */
	private static final double GAP = 0.5;

	public double length() {
		double l = 0;
		for (float f : lengths) {
			l += f + GAP;
		}
		return l - GAP;
	}

	// ------------------------------------------------------------------------------------------------
	// The way

	private static boolean track(BlockState s) {
		return s.getBlock() instanceof TrackBlock;
	}

	private void addPoint(BlockPos p, BlockState s) {
		double[] q = {p.getX() + 0.5, p.getY() + TrackBlock.railTop(s), p.getZ() + 0.5};
		if (!pts.isEmpty()) {
			double[] last = pts.getLast();
			cum.add(cum.getLast() + Math.sqrt((q[0] - last[0]) * (q[0] - last[0]) + (q[1] - last[1]) * (q[1] - last[1]) + (q[2] - last[2]) * (q[2] - last[2])));
		} else {
			cum.add(0.0);
		}
		pts.add(q);
		lastCell = p.immutable();
	}

	/** Starts the way at a track block, heading (mx, mz). */
	public void start(BlockPos p, int mx, int mz) {
		pts.clear();
		cum.clear();
		ends = false;
		addPoint(p, level().getBlockState(p));
		lastMx = mx;
		lastMz = mz;
	}

	/** The next block of track from the last one, going on the way it went (a turn of 45 degrees at most). */
	private boolean extendOne() {
		if (ends || lastCell == null) {
			return false;
		}
		Level level = level();
		BlockPos best = null;
		BlockState bestState = null;
		int bestDot = 0;
		int bdx = 0;
		int bdz = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int dot = dx * lastMx + dz * lastMz;
				if (dot <= 0 || dot < bestDot) {
					continue;
				}
				for (int dy : new int[]{0, 1, -1}) {
					BlockPos q = lastCell.offset(dx, dy, dz);
					if (!level.hasChunkAt(q)) {
						open = false;
						return false;
					}
					BlockState s = level.getBlockState(q);
					if (track(s)) {
						if (dot > bestDot) {
							best = q;
							bestState = s;
							bestDot = dot;
							bdx = dx;
							bdz = dz;
						}
						break;
					}
				}
			}
		}
		if (best == null) {
			ends = true;
			return false;
		}
		lastMx = bdx;
		lastMz = bdz;
		addPoint(best, bestState);
		return true;
	}

	/** Traces the way out from a block for up to {@code max} blocks (for spawning): the cells, in order. */
	public static List<BlockPos> trace(Level level, BlockPos from, int mx, int mz, int max) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos at = from;
		for (int i = 0; i < max; i++) {
			BlockPos next = null;
			int bestDot = 0;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int dot = dx * mx + dz * mz;
					if (dot <= 0 || dot <= bestDot) {
						continue;
					}
					for (int dy : new int[]{0, 1, -1}) {
						BlockPos q = at.offset(dx, dy, dz);
						if (level.hasChunkAt(q) && track(level.getBlockState(q))) {
							next = q;
							bestDot = dot;
							break;
						}
					}
				}
			}
			if (next == null) {
				break;
			}
			mx = next.getX() - at.getX();
			mz = next.getZ() - at.getZ();
			out.add(next);
			at = next;
		}
		return out;
	}

	/** Is the end of the line (a buffer stop) right after this block, going (mx, mz)? */
	public static boolean atBuffer(Level level, BlockPos p, int mx, int mz) {
		for (int dy = -1; dy <= 1; dy++) {
			if (level.getBlockState(p.offset(mx, dy, mz)).is(StreetBlocks.BUFFER_STOP)) {
				return true;
			}
		}
		return false;
	}

	/** The point at distance s along the way. */
	private double[] at(double s) {
		int n = pts.size();
		if (n == 1) {
			return pts.getFirst();
		}
		int lo = 0;
		int hi = n - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (cum.get(mid) <= s) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		double a = cum.get(lo);
		double b = cum.get(hi);
		double f = b > a ? Math.max(0, Math.min(1.2, (s - a) / (b - a))) : 0;
		if (s < cum.getFirst()) {
			f = (s - a) / Math.max(1e-6, b - a);
		}
		double[] p = pts.get(lo);
		double[] q = pts.get(hi);
		return new double[]{p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f, p[2] + (q[2] - p[2]) * f};
	}

	/** The way's end ahead (distance along). */
	private double wayEnd() {
		return cum.getLast();
	}

	/** Turns the train round (an electric train at the end of the line): the last car leads now. */
	private void reverse() {
		double l = wayEnd();
		double tail = head - length();
		java.util.Collections.reverse(pts);
		List<Double> c = new ArrayList<>();
		for (int i = cum.size() - 1; i >= 0; i--) {
			c.add(l - cum.get(i));
		}
		cum.clear();
		cum.addAll(c);
		head = l - tail;
		java.util.Collections.reverse(cars);
		java.util.Collections.reverse(lengths);
		java.util.Collections.reverse(flipped);
		for (int i = 0; i < flipped.size(); i++) {
			flipped.set(i, !flipped.get(i));
		}
		double[] a = pts.get(pts.size() - 2);
		double[] b = pts.getLast();
		lastMx = (int) Math.signum(Math.round(b[0] - a[0]));
		lastMz = (int) Math.signum(Math.round(b[2] - a[2]));
		lastCell = BlockPos.containing(b[0], b[1] - 0.2, b[2]);
		ends = false;
		open = true;
	}

	/** One tick of running (called on the client). */
	public void runTick() {
		if (pts.isEmpty()) {
			done = true;
			return;
		}
		while (open && !ends && wayEnd() - head < 90) {
			if (!extendOne()) {
				break;
			}
		}
		// Forget the way well behind the train.
		double tail = head - length();
		while (pts.size() > 2 && cum.get(1) < tail - 24) {
			pts.removeFirst();
			cum.removeFirst();
		}
		double room = wayEnd() - head - (ends ? 1.0 : 0.5);
		double brake = electric ? 0.0045 : 0.003;
		double limit = Math.sqrt(Math.max(0, 2 * brake * room));
		if (waiting > 0) {
			speed = 0;
			if (--waiting == 0) {
				if (electric) {
					reverse();
					departing = true;
				} else {
					done = true;
				}
			}
		} else {
			double want = Math.min(top, limit);
			speed = speed < want ? Math.min(want, speed + 0.006) : Math.max(want, speed - brake * 1.5);
			if (room < 0.15 && speed < 0.02) {
				speed = 0;
				if (ends) {
					stops++;
					waiting = electric ? 500 : 300;
				} else {
					stuck++;
				}
			} else {
				stuck = 0;
			}
		}
		head += speed;
		run += speed;
		poseO = pose;
		pose = new double[cars.size()][];
		double front = head;
		for (int i = 0; i < cars.size(); i++) {
			double len = lengths.get(i);
			double bogie = len * 0.36;
			double mid = front - len / 2;
			double[] a = at(mid + bogie);
			double[] b = at(mid - bogie);
			double dx = a[0] - b[0];
			double dy = a[1] - b[1];
			double dz = a[2] - b[2];
			double yaw = Math.toDegrees(Math.atan2(-dx, dz));
			double pitch = Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			pose[i] = new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2, yaw, pitch};
			front -= len + GAP;
		}
		if (poseO.length != pose.length) {
			poseO = pose;
		}
		setPos(pose[0][0], pose[0][1], pose[0][2]);
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			discard();
			return;
		}
		tickCount++;
		setOldPosAndRot();
		if (clientTick != null) {
			clientTick.accept(this);
		}
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
