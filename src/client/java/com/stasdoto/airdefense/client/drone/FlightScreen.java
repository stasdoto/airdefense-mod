package com.stasdoto.airdefense.client.drone;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.stasdoto.airdefense.client.map.TacticalMapScreen;
import com.stasdoto.airdefense.drone.FlightPlan;
import com.stasdoto.airdefense.drone.FlightPlanPayload;
import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.launcher.LauncherType;
import com.stasdoto.airdefense.map.MapStatusPayload;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * The flight task window for drones and cruise missiles: where to (typed in, the tablet's mark, your own position
 * or the block you look at), how high, how fast, which way, how many, and whether to watch through the camera.
 * Shows the distance and the time in flight before you press "Launch".
 */
public class FlightScreen extends Screen {
	private static final int C_TEXT = 0xFFE6E9EC;
	private static final int C_DIM = 0xFF9AA4AE;
	private static final int C_OK = 0xFF8AE07A;
	private static final int C_BAD = 0xFFFF7A6A;
	private static final int C_HEAD = 0xFFFFD24A;

	private final MapStatusPayload.Entry vehicle;
	private final VehicleType type;
	private EditBox xBox;
	private EditBox zBox;
	private int altitude = 50;
	private int speed = 100;
	private int maneuver = FlightPlan.WEAVE;
	private int count;
	private boolean camera;
	private Button maneuverButton;
	private Button cameraButton;
	private Button launchButton;
	private int x0;
	private int y0;
	private static final int W = 300;
	private static final int H = 214;

	public FlightScreen(MapStatusPayload.Entry vehicle, @Nullable BlockPos target) {
		super(Component.translatable("screen.airdefense.flight.title"));
		this.vehicle = vehicle;
		this.type = VehicleType.byId(vehicle.type());
		this.count = Math.max(1, Integer.bitCount(vehicle.loaded()));
		if (target != null) {
			pendingX = target.getX();
			pendingZ = target.getZ();
		}
		if (type.launcher != null && type.launcher.missile.kind == MissileType.Kind.CRUISE) {
			altitude = 40;
			maneuver = FlightPlan.STRAIGHT;
		}
	}

	@Nullable
	private Integer pendingX;
	@Nullable
	private Integer pendingZ;

	@Override
	protected void init() {
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		int y = y0 + 22;
		xBox = addRenderableWidget(new EditBox(font, x0 + 26, y, 60, 16, Component.literal("X")));
		zBox = addRenderableWidget(new EditBox(font, x0 + 110, y, 60, 16, Component.literal("Z")));
		xBox.setMaxLength(9);
		zBox.setMaxLength(9);
		if (pendingX != null) {
			xBox.setValue(String.valueOf(pendingX));
			zBox.setValue(String.valueOf(pendingZ));
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.flight.mark"), b -> fromMark())
				.bounds(x0 + 178, y - 1, 38, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.flight.me"), b -> fromMe())
				.bounds(x0 + 218, y - 1, 34, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.flight.look"), b -> fromLook())
				.bounds(x0 + 254, y - 1, 40, 18).build());
		y += 26;
		stepper(y, () -> altitude = Mth.clamp(altitude - 5, FlightPlan.MIN_ALT, FlightPlan.MAX_ALT),
				() -> altitude = Mth.clamp(altitude + 5, FlightPlan.MIN_ALT, FlightPlan.MAX_ALT));
		y += 22;
		stepper(y, () -> speed = Mth.clamp(speed - 10, 50, 100), () -> speed = Mth.clamp(speed + 10, 50, 100));
		y += 22;
		maneuverButton = addRenderableWidget(Button.builder(Component.empty(), b -> maneuver = (maneuver + 1) % FlightPlan.MANEUVERS)
				.bounds(x0 + 150, y - 2, 144, 18).build());
		y += 22;
		stepper(y, () -> count = Math.max(1, count - 1), () -> count = Math.min(Math.max(1, Integer.bitCount(vehicle.loaded())), count + 1));
		y += 22;
		cameraButton = addRenderableWidget(Button.builder(Component.empty(), b -> camera = !camera).bounds(x0 + 150, y - 2, 144, 18).build());
		launchButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.flight.launch"), b -> launch())
				.bounds(x0 + 6, y0 + H - 26, 140, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.flight.back"), b -> back())
				.bounds(x0 + W - 146, y0 + H - 26, 140, 20).build());
		update();
	}

	private void stepper(int y, Runnable minus, Runnable plus) {
		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			minus.run();
			update();
		}).bounds(x0 + 150, y - 2, 20, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			plus.run();
			update();
		}).bounds(x0 + 274, y - 2, 20, 18).build());
	}

	private void update() {
		maneuverButton.setMessage(Component.translatable("screen.airdefense.flight.maneuver." + maneuver));
		cameraButton.setMessage(Component.translatable(camera ? "screen.airdefense.flight.camera_on" : "screen.airdefense.flight.camera_off"));
		launchButton.active = target() != null && problem() == null;
	}

	@Override
	public void tick() {
		Player p = minecraft.player;
		if (p == null || DesignatorItem.held(p) == null) {
			onClose();
			return;
		}
		update();
	}

	@Nullable
	private int[] target() {
		try {
			return new int[]{Integer.parseInt(xBox.getValue().trim()), Integer.parseInt(zBox.getValue().trim())};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private double distance() {
		int[] t = target();
		return t == null ? -1 : Math.hypot(t[0] + 0.5 - vehicle.x(), t[1] + 0.5 - vehicle.z());
	}

	@Nullable
	private Component problem() {
		LauncherType l = type.launcher;
		double d = distance();
		if (l == null || d < 0) {
			return Component.translatable("screen.airdefense.flight.no_target");
		}
		if (d < 24) {
			return Component.translatable("screen.airdefense.map.too_close", (int) d);
		}
		if (d > l.maxRange) {
			return Component.translatable("screen.airdefense.map.out_of_range", (int) d, l.maxRange);
		}
		if (vehicle.loaded() == 0) {
			return Component.translatable("screen.airdefense.map.st.empty");
		}
		if (vehicle.firing() || vehicle.busy() > 0) {
			return Component.translatable("screen.airdefense.map.st.reload", vehicle.busy() / 20 + 1);
		}
		return null;
	}

	/** Seconds in the air: distance (longer round the flank) at the chosen speed. */
	private double flightSeconds() {
		LauncherType l = type.launcher;
		double d = distance();
		if (l == null || d < 0) {
			return -1;
		}
		double route = maneuver == FlightPlan.FLANK ? 1.28 : maneuver == FlightPlan.WEAVE ? 1.06 : 1.0;
		double v = l.missile.maxSpeed * speed / 100.0;
		return (d * route + altitude) / v / 20.0 + 2;
	}

	private void fromMark() {
		Player p = minecraft.player;
		BlockPos t = p != null ? DesignatorItem.getTarget(DesignatorItem.held(p)) : null;
		if (t != null) {
			set(t.getX(), t.getZ());
		}
	}

	private void fromMe() {
		Player p = minecraft.player;
		if (p != null) {
			set(p.getBlockX(), p.getBlockZ());
		}
	}

	private void fromLook() {
		Player p = minecraft.player;
		if (p == null) {
			return;
		}
		HitResult hit = p.pick(DesignatorItem.RANGE, 1.0f, false);
		if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
			set(b.getBlockPos().getX(), b.getBlockPos().getZ());
		}
	}

	private void set(int x, int z) {
		xBox.setValue(String.valueOf(x));
		zBox.setValue(String.valueOf(z));
		update();
	}

	private void launch() {
		int[] t = target();
		if (t == null || problem() != null) {
			return;
		}
		FlightPlan plan = new FlightPlan(altitude, speed, maneuver, count, camera);
		if (ClientPlayNetworking.canSend(FlightPlanPayload.TYPE)) {
			ClientPlayNetworking.send(new FlightPlanPayload(vehicle.id(), t[0], t[1], plan));
		}
		if (camera) {
			onClose();
		} else {
			back();
		}
	}

	private void back() {
		minecraft.gui.setScreen(new TacticalMapScreen());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xC00C1014);
		g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF3A4652);
		g.fill(x0, y0, x0 + W, y0 + H, 0xFF161B21);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Component head = Component.translatable("screen.airdefense.flight.head", Component.translatable("entity.airdefense." + type.id));
		g.text(font, head, x0 + 6, y0 + 7, C_HEAD);
		int y = y0 + 22;
		g.text(font, "X", x0 + 14, y + 4, C_TEXT);
		g.text(font, "Z", x0 + 98, y + 4, C_TEXT);
		y += 26;
		row(g, y, "screen.airdefense.flight.altitude", altitude + " m");
		y += 22;
		LauncherType l = type.launcher;
		int kmh = l == null ? 0 : (int) Math.round(l.missile.maxSpeed * speed / 100.0 * 20 * 3.6);
		row(g, y, "screen.airdefense.flight.speed", speed + "% · " + kmh + " km/h");
		y += 22;
		g.text(font, Component.translatable("screen.airdefense.flight.route"), x0 + 6, y + 3, C_TEXT);
		y += 22;
		row(g, y, "screen.airdefense.flight.count", count + " / " + Integer.bitCount(vehicle.loaded()));
		y += 22;
		g.text(font, Component.translatable("screen.airdefense.flight.camera"), x0 + 6, y + 3, C_TEXT);
		y += 24;
		double d = distance();
		Component why = problem();
		if (d >= 0) {
			String info = Component.translatable("screen.airdefense.flight.info", (int) d, String.format(Locale.ROOT, "%.0f", flightSeconds())).getString();
			g.text(font, info, x0 + 6, y, why == null ? C_OK : C_DIM);
		}
		if (why != null) {
			g.text(font, why, x0 + 6, y + 11, C_BAD);
		} else {
			g.text(font, Component.translatable("screen.airdefense.flight.hint." + maneuver), x0 + 6, y + 11, C_DIM);
		}
	}

	private void row(GuiGraphicsExtractor g, int y, String key, String value) {
		g.text(font, Component.translatable(key), x0 + 6, y + 3, C_TEXT);
		g.text(font, value, x0 + 222 - font.width(value) / 2, y + 3, C_HEAD);
	}
}
