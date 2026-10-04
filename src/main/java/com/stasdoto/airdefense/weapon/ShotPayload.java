package com.stasdoto.airdefense.weapon;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import com.stasdoto.airdefense.AirDefense;

/**
 * Server to clients: someone fired. The clients draw the muzzle flash, the tracer, the impact and play the report.
 * The shooter's own client has drawn all that already when the button went down; it only uses {@code hit} for the
 * hit marker.
 */
public record ShotPayload(int shooter, int gun, double fx, double fy, double fz, double tx, double ty, double tz, int hit, int round)
		implements CustomPacketPayload {
	public static final int HIT_NONE = 0;
	public static final int HIT_BLOCK = 1;
	/** A living target (blood). */
	public static final int HIT_FLESH = 2;
	/** A vehicle or a missile (sparks). */
	public static final int HIT_METAL = 3;
	/** A living target, in the head. */
	public static final int HIT_HEAD = 4;

	public static final Type<ShotPayload> TYPE = new Type<>(AirDefense.id("shot"));
	public static final StreamCodec<ByteBuf, ShotPayload> CODEC = new StreamCodec<>() {
		@Override
		public ShotPayload decode(ByteBuf buf) {
			return new ShotPayload(ByteBufCodecs.VAR_INT.decode(buf), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte(), ByteBufCodecs.VAR_INT.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, ShotPayload p) {
			ByteBufCodecs.VAR_INT.encode(buf, p.shooter);
			buf.writeByte(p.gun);
			buf.writeDouble(p.fx);
			buf.writeDouble(p.fy);
			buf.writeDouble(p.fz);
			buf.writeDouble(p.tx);
			buf.writeDouble(p.ty);
			buf.writeDouble(p.tz);
			buf.writeByte(p.hit);
			ByteBufCodecs.VAR_INT.encode(buf, p.round);
		}
	};

	public Vec3 from() {
		return new Vec3(fx, fy, fz);
	}

	public Vec3 to() {
		return new Vec3(tx, ty, tz);
	}

	public GunType gunType() {
		return GunType.byId(gun);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
