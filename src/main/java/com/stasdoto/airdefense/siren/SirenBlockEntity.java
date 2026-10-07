package com.stasdoto.airdefense.siren;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.stasdoto.airdefense.registry.ModBlockEntities;

/**
 * A siren's own setting (auto / on / off) and its town. Once a second the server works out what it should sound
 * and puts that in the block state, which every client nearby sees - the client plays the sound (see SirenClient).
 */
public class SirenBlockEntity extends BlockEntity {
	private int mode = Sirens.MODE_AUTO;
	private int town = Integer.MIN_VALUE;
	private boolean registered;
	private int seconds;

	public SirenBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SIREN, pos, state);
	}

	public int mode() {
		return mode;
	}

	public void setMode(int mode) {
		this.mode = mode;
		setChanged();
		if (level instanceof ServerLevel server) {
			if (town == Integer.MIN_VALUE) {
				town = Sirens.townOf(server, getBlockPos());
			}
			update(server, getBlockPos(), getBlockState());
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SirenBlockEntity be) {
		if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.hashCode()) % 20 != 0) {
			return;
		}
		if (!be.registered) {
			be.registered = true;
			Sirens.get(server.getServer()).register(pos);
		}
		if (be.town == Integer.MIN_VALUE || ++be.seconds % 30 == 0) {
			// The town may be founded after the siren was built: look again now and then.
			be.town = Sirens.townOf(server, pos);
		}
		be.update(server, pos, state);
	}

	private void update(ServerLevel server, BlockPos pos, BlockState state) {
		SirenBlock.Signal want = SirenBlock.Signal.of(Sirens.get(server.getServer()).signalFor(server.getGameTime(), town, mode));
		if (state.getValue(SirenBlock.SIGNAL) != want) {
			server.setBlock(pos, state.setValue(SirenBlock.SIGNAL, want), Block.UPDATE_ALL);
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, SirenBlockEntity be) {
		SirenSounds.report(pos, state.getValue(SirenBlock.SIGNAL));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("mode", mode);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		mode = input.getIntOr("mode", Sirens.MODE_AUTO);
	}
}
