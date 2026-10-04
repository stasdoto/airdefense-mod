package com.stasdoto.airdefense.nation;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/** Server to client: everything the village screen shows. {@code problem} is why the charter cannot be bought ("" = it can). */
public record SettlementInfoPayload(boolean open, int id, String name, String country, int color, boolean cityState, boolean mine,
		int population, int guards, int soldiers, int mobilizable, int reputation, int price, String problem, boolean creative,
		String elder, int war, int tribute) implements CustomPacketPayload {
	public static final Type<SettlementInfoPayload> TYPE = new Type<>(AirDefense.id("settlement_info"));
	public static final StreamCodec<ByteBuf, SettlementInfoPayload> CODEC = new StreamCodec<>() {
		@Override
		public SettlementInfoPayload decode(ByteBuf b) {
			return new SettlementInfoPayload(b.readBoolean(), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
					ByteBufCodecs.STRING_UTF8.decode(b), b.readInt(), b.readBoolean(), b.readBoolean(), ByteBufCodecs.VAR_INT.decode(b),
					ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), b.readInt(),
					ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), b.readBoolean(), ByteBufCodecs.STRING_UTF8.decode(b),
					ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b));
		}

		@Override
		public void encode(ByteBuf b, SettlementInfoPayload p) {
			b.writeBoolean(p.open);
			ByteBufCodecs.VAR_INT.encode(b, p.id);
			ByteBufCodecs.STRING_UTF8.encode(b, p.name);
			ByteBufCodecs.STRING_UTF8.encode(b, p.country);
			b.writeInt(p.color);
			b.writeBoolean(p.cityState);
			b.writeBoolean(p.mine);
			ByteBufCodecs.VAR_INT.encode(b, p.population);
			ByteBufCodecs.VAR_INT.encode(b, p.guards);
			ByteBufCodecs.VAR_INT.encode(b, p.soldiers);
			ByteBufCodecs.VAR_INT.encode(b, p.mobilizable);
			b.writeInt(p.reputation);
			ByteBufCodecs.VAR_INT.encode(b, p.price);
			ByteBufCodecs.STRING_UTF8.encode(b, p.problem);
			b.writeBoolean(p.creative);
			ByteBufCodecs.STRING_UTF8.encode(b, p.elder);
			ByteBufCodecs.VAR_INT.encode(b, p.war);
			ByteBufCodecs.VAR_INT.encode(b, p.tribute);
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
