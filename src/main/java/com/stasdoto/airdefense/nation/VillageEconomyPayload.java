package com.stasdoto.airdefense.nation;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.stasdoto.airdefense.AirDefense;

/**
 * Server to client: the household of one of the player's villages (for the village screen's work, building and
 * hangar tabs). {@code queue} holds the unfinished buildings as type * 1000 + percent built; {@code hangarQueue}
 * the vehicles on order (vehicle type ordinals), the first being made.
 */
public record VillageEconomyPayload(int id, int wood, int stone, int iron, int cap, boolean free, List<Integer> jobs, int idle,
		int beds, int freeBeds, int births, List<Integer> built, List<Integer> queue, int builders, boolean hangar,
		List<Integer> hangarQueue, int hangarPercent, int birthEvery, int mood, List<Integer> moodReasons, int rebels, int calmPrice,
		List<Integer> extra)
		implements CustomPacketPayload {
	public static final Type<VillageEconomyPayload> TYPE = new Type<>(AirDefense.id("village_economy"));
	private static final StreamCodec<ByteBuf, List<Integer>> INTS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64));
	public static final StreamCodec<ByteBuf, VillageEconomyPayload> CODEC = new StreamCodec<>() {
		@Override
		public VillageEconomyPayload decode(ByteBuf b) {
			return new VillageEconomyPayload(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b),
					ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), b.readBoolean(), INTS.decode(b), ByteBufCodecs.VAR_INT.decode(b),
					ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), INTS.decode(b), INTS.decode(b),
					ByteBufCodecs.VAR_INT.decode(b), b.readBoolean(), INTS.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b),
					b.readInt(), INTS.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), INTS.decode(b));
		}

		@Override
		public void encode(ByteBuf b, VillageEconomyPayload p) {
			ByteBufCodecs.VAR_INT.encode(b, p.id);
			ByteBufCodecs.VAR_INT.encode(b, p.wood);
			ByteBufCodecs.VAR_INT.encode(b, p.stone);
			ByteBufCodecs.VAR_INT.encode(b, p.iron);
			ByteBufCodecs.VAR_INT.encode(b, p.cap);
			b.writeBoolean(p.free);
			INTS.encode(b, p.jobs);
			ByteBufCodecs.VAR_INT.encode(b, p.idle);
			ByteBufCodecs.VAR_INT.encode(b, p.beds);
			ByteBufCodecs.VAR_INT.encode(b, p.freeBeds);
			ByteBufCodecs.VAR_INT.encode(b, p.births);
			INTS.encode(b, p.built);
			INTS.encode(b, p.queue);
			ByteBufCodecs.VAR_INT.encode(b, p.builders);
			b.writeBoolean(p.hangar);
			INTS.encode(b, p.hangarQueue);
			ByteBufCodecs.VAR_INT.encode(b, p.hangarPercent);
			ByteBufCodecs.VAR_INT.encode(b, p.birthEvery);
			b.writeInt(p.mood);
			INTS.encode(b, p.moodReasons);
			ByteBufCodecs.VAR_INT.encode(b, p.rebels);
			ByteBufCodecs.VAR_INT.encode(b, p.calmPrice);
			INTS.encode(b, p.extra);
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public int stock(int kind) {
		return kind == 0 ? wood : kind == 1 ? stone : iron;
	}

	public int builtCount(BuildingType type) {
		return type.ordinal() < built.size() ? built.get(type.ordinal()) : 0;
	}
}
