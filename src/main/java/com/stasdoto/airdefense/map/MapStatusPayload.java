package com.stasdoto.airdefense.map;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Server → tablet map: every vehicle in the dimension with what the map needs to show and command it. */
public record MapStatusPayload(List<Entry> vehicles) implements CustomPacketPayload {
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
	}

	private static MapStatusPayload read(ByteBuf buf) {
		int n = buf.readShort();
		List<Entry> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(new Entry(buf.readInt(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					buf.readByte(), buf.readByte(), buf.readInt(), buf.readShort(), buf.readByte(), buf.readInt(), buf.readBoolean(),
					buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
		}
		return new MapStatusPayload(list);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
