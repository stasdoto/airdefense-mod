package com.stasdoto.airdefense.map;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * Server → the tablet's radar screen: the radar stations working now and everything in the air they see.
 * A contact's {@code type} is what the radar takes it for (a decoy shows as the real thing unless recognised).
 */
public record RadarPayload(List<Station> stations, List<Contact> contacts) implements CustomPacketPayload {
	public record Station(int id, int vehicleType, float x, float y, float z, float yaw) {
	}

	/**
	 * {@code hostile} = a threat (missile, drone), otherwise one of ours (interceptor); {@code decoy} = recognised as
	 * a decoy; target and launch point on the ground; {@code engaged} = interceptors on their way to it.
	 */
	public record Contact(int id, int type, boolean hostile, boolean decoy, float x, float y, float z, float vx, float vy, float vz,
			float tx, float ty, float tz, float lx, float lz, int engaged, float height) {
	}

	public static final Type<RadarPayload> TYPE = new Type<>(AirDefense.id("radar"));
	public static final StreamCodec<ByteBuf, RadarPayload> CODEC = StreamCodec.of(RadarPayload::write, RadarPayload::read);

	private static void write(ByteBuf buf, RadarPayload p) {
		buf.writeShort(p.stations.size());
		for (Station s : p.stations) {
			buf.writeInt(s.id);
			buf.writeByte(s.vehicleType);
			buf.writeFloat(s.x);
			buf.writeFloat(s.y);
			buf.writeFloat(s.z);
			buf.writeFloat(s.yaw);
		}
		buf.writeShort(p.contacts.size());
		for (Contact c : p.contacts) {
			buf.writeInt(c.id);
			buf.writeByte(c.type);
			buf.writeBoolean(c.hostile);
			buf.writeBoolean(c.decoy);
			buf.writeFloat(c.x);
			buf.writeFloat(c.y);
			buf.writeFloat(c.z);
			buf.writeFloat(c.vx);
			buf.writeFloat(c.vy);
			buf.writeFloat(c.vz);
			buf.writeFloat(c.tx);
			buf.writeFloat(c.ty);
			buf.writeFloat(c.tz);
			buf.writeFloat(c.lx);
			buf.writeFloat(c.lz);
			buf.writeByte(c.engaged);
			buf.writeFloat(c.height);
		}
	}

	private static RadarPayload read(ByteBuf buf) {
		int n = buf.readShort();
		List<Station> stations = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			stations.add(new Station(buf.readInt(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
		}
		int m = buf.readShort();
		List<Contact> contacts = new ArrayList<>(m);
		for (int i = 0; i < m; i++) {
			contacts.add(new Contact(buf.readInt(), buf.readByte(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(),
					buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readFloat()));
		}
		return new RadarPayload(stations, contacts);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
