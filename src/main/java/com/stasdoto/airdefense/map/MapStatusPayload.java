package com.stasdoto.airdefense.map;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Server → tablet map: every vehicle in the dimension with what the map needs to show and command it. */
public record MapStatusPayload(List<Entry> vehicles, List<Fire> fires, List<Spot> spots, List<Eye> eyes) implements CustomPacketPayload {
	/**
	 * 1.34: something the player's reconnaissance drones have seen: where, a vehicle (its type) or soldiers (type -1),
	 * how long ago it was last seen (s).
	 */
	public record Spot(int x, int y, int z, int type, int age) {
	}

	/** 1.34: one of the player's reconnaissance drones over the field: where, how far it sees, which drone. */
	public record Eye(int x, int z, int range, int type) {
	}

	/** 1.30: an enemy firing position found by the player's counter-battery radars: where, how long ago (s), rounds seen. */
	public record Fire(int x, int y, int z, int age, int rounds) {
	}

	/**
	 * One vehicle. {@code busy} = ticks until ready (launcher reload or air defence reload), {@code tracked} = entity id
	 * of the threat an air defence is engaging (or -1), target = launcher's current aim point (if {@code hasTarget}).
	 */
	public record Entry(int id, int type, float x, float y, float z, float yaw, int state, int mode, int loaded, int ammo,
			int health, int busy, boolean firing, boolean hasTarget, int tx, int ty, int tz, int tracked, int reserve) {
	}

	public static final Type<MapStatusPayload> TYPE = new Type<>(AirDefense.id("map_status"));
	public static final StreamCodec<ByteBuf, MapStatusPayload> CODEC = StreamCodec.of(MapStatusPayload::write, MapStatusPayload::read);

	private static void write(ByteBuf buf, MapStatusPayload p) {
		buf.writeShort(p.vehicles.size());
		for (Entry e : p.vehicles) {
			buf.writeInt(e.id);
			buf.writeByte(e.type);
			buf.writeFloat(e.x);
			buf.writeFloat(e.y);
			buf.writeFloat(e.z);
			buf.writeFloat(e.yaw);
			buf.writeByte(e.state);
			buf.writeByte(e.mode);
			buf.writeInt(e.loaded);
			buf.writeShort(e.ammo);
			buf.writeByte(e.health);
			buf.writeInt(e.busy);
			buf.writeBoolean(e.firing);
			buf.writeBoolean(e.hasTarget);
			buf.writeInt(e.tx);
			buf.writeInt(e.ty);
			buf.writeInt(e.tz);
			buf.writeInt(e.tracked);
			buf.writeInt(e.reserve);
		}
		buf.writeShort(p.fires.size());
		for (Fire f : p.fires) {
			buf.writeInt(f.x);
			buf.writeInt(f.y);
			buf.writeInt(f.z);
			buf.writeShort(f.age);
			buf.writeShort(f.rounds);
		}
		buf.writeShort(p.spots.size());
		for (Spot s : p.spots) {
			buf.writeInt(s.x);
			buf.writeShort(s.y);
			buf.writeInt(s.z);
			buf.writeShort(s.type);
			buf.writeShort(s.age);
		}
		buf.writeShort(p.eyes.size());
		for (Eye e : p.eyes) {
			buf.writeInt(e.x);
			buf.writeInt(e.z);
			buf.writeShort(e.range);
			buf.writeShort(e.type);
		}
	}

	private static MapStatusPayload read(ByteBuf buf) {
		int n = buf.readShort();
		List<Entry> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(new Entry(buf.readInt(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					buf.readByte(), buf.readByte(), buf.readInt(), buf.readShort(), buf.readByte(), buf.readInt(), buf.readBoolean(),
					buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
		}
		int nf = buf.readShort();
		List<Fire> fires = new ArrayList<>(nf);
		for (int i = 0; i < nf; i++) {
			fires.add(new Fire(buf.readInt(), buf.readInt(), buf.readInt(), buf.readShort(), buf.readShort()));
		}
		int ns = buf.readShort();
		List<Spot> spots = new ArrayList<>(ns);
		for (int i = 0; i < ns; i++) {
			spots.add(new Spot(buf.readInt(), buf.readShort(), buf.readInt(), buf.readShort(), buf.readShort()));
		}
		int ne = buf.readShort();
		List<Eye> eyes = new ArrayList<>(ne);
		for (int i = 0; i < ne; i++) {
			eyes.add(new Eye(buf.readInt(), buf.readInt(), buf.readShort(), buf.readShort()));
		}
		return new MapStatusPayload(list, fires, spots, eyes);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
