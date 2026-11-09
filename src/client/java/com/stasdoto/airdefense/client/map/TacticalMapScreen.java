package com.stasdoto.airdefense.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.stasdoto.airdefense.client.ui.UiButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.map.Grid;
import com.stasdoto.airdefense.map.MapActionPayload;
import com.stasdoto.airdefense.map.MapStatusPayload;
import com.stasdoto.airdefense.missile.MissileEntity;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * The tablet's tactical map: explored terrain, every vehicle with its state, missiles in the air, air defence coverage.
 * Pick a launcher on the map or in the list, click where to strike, press "Fire!". Air defence batteries are switched
 * on and off from here too. The game keeps running while the map is open.
 */
public class TacticalMapScreen extends Screen {
	/** GUI pixels per block. */
	/** Pixels per block; the last two show the whole atlas (10 km). */
	private static final float[] ZOOMS = {4f, 2f, 1f, 0.5f, 0.25f, 0.125f, 0.0625f, 0.03125f};
	private static final int PANEL_W = 150;
	private static final int ROW_H = 20;
	private static final int C_LAUNCHER = 0xFFE8A33C;
	private static final int C_DEFENSE = 0xFF4FB8E8;
	private static final int C_RADAR = 0xFF5FE07A;
	private static final int C_TARGET = 0xFFFF4A3A;
	private static final int C_TEXT = com.stasdoto.airdefense.client.ui.Ui.TEXT;
	private static final int C_DIM = com.stasdoto.airdefense.client.ui.Ui.DIM;
	private static final int C_BAD = com.stasdoto.airdefense.client.ui.Ui.BAD;

	private double centerX;
	private double centerZ;
	private int zoom = 2;
	private boolean follow = true;
	private boolean initialised;
	private int selected = -1;
	@Nullable
	private BlockPos target;
	private int listScroll;
	private int refresh;
	/** Ticks to keep a target just picked (or cleared) on this screen before trusting the tablet's copy again. */
	private int localChange;
	private boolean pressedInMap;
	private boolean dragMoved;
	private double pressX;
	private double pressY;

	private int mx0;
	private int my0;
	private int mx1;
	private int my1;
	private int px0;
	private int listTop;
	private int listBottom;

	private UiButton fireButton;
	private UiButton modeButton;
	private UiButton clearButton;
	private UiButton meButton;
	/** Panel tab: 0 = vehicles, 1 = the strike planner. */
	private int tab;
	/** Lite: which side the tablet commands (header button). */
	private UiButton sideButton;
	private UiButton planButton;
	private UiButton massButton;
	/** 1.30: how many rounds the selected gun fires (0 = its usual fire mission). */
	private UiButton roundsButton;
	private int rounds;
	private static final int[] GUN_ROUNDS = {0, 1, 3, 12, 30};
	private static final int[] ROCKET_ROUNDS = {0, 10, 20};
	/** The planner's buttons down the panel, and its chooser (the salvo). */
	private final UiButton[] cmd = new UiButton[6];
	private final UiButton[] row = new UiButton[3];
	public TacticalMapScreen() {
		super(Component.translatable("screen.airdefense.map.title"));
	}

	@Override
	protected void init() {
		mx0 = 6;
		my0 = 18;
		mx1 = width - PANEL_W - 10;
		my1 = height - 16;
		px0 = width - PANEL_W - 4;
		int pw = PANEL_W - 8;
		listTop = my0 + 16;
		listBottom = my1 - 70;
		fireButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.fire"), b -> fire())
				.bounds(px0 + 4, my1 - 20, pw, 20).style(UiButton.Style.DANGER).build());
		modeButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.mode_auto"), b -> toggleMode())
				.bounds(px0 + 4, my1 - 20, pw, 20).build());
		clearButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.clear"), b -> clearTarget())
				.bounds(px0 + 4, my1 - 42, pw / 2 - 1, 20).build());
		UiButton radarButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.to_radar"),
				b -> minecraft.gui.setScreen(new RadarScreen())).bounds(mx0, 2, 70, 13).build());
		massButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.mass_strike"), b -> massStrike())
				.bounds(mx0, 2, 110, 13).build());
		UiButton sirenButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.sirens"),
						b -> minecraft.gui.setScreen(new com.stasdoto.airdefense.client.siren.SirenScreen()))
				.bounds(mx0, 2, 80, 13).build());
		planButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.plan"), b -> openPlan())
				.bounds(px0 + 4 + pw / 2 + 1, my1 - 20, pw - pw / 2 - 1, 20).build());
		meButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.me"), b -> follow = true)
				.bounds(px0 + 4 + pw / 2 + 1, my1 - 42, pw - pw / 2 - 1, 20).build());
		roundsButton = addRenderableWidget(UiButton.create(Component.empty(), b -> cycleRounds())
				.bounds(px0 + 4 + pw / 2 + 1, my1 - 20, pw - pw / 2 - 1, 20).build());
		pauseButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.plan.pause"), b -> setPaused(!paused))
				.bounds(mx0, 2, 62, 13).build());
		plannerButton = addRenderableWidget(UiButton.create(Component.translatable("screen.airdefense.map.plan.open_short"),
				b -> setTab(tab == 1 ? 0 : 1)).bounds(mx0, 2, 70, 13).build());
		sideButton = addRenderableWidget(UiButton.create(Component.translatable(sideKey()), b -> toggleSide()).bounds(mx0, 2, 90, 13).build());
		layoutHeader(new UiButton[]{sideButton, radarButton, massButton, sirenButton, pauseButton, plannerButton},
				new String[][]{{"screen.airdefense.map.side.ours", "screen.airdefense.map.side.enemy"}, {"screen.airdefense.map.to_radar"},
						{"screen.airdefense.map.mass_strike"}, {"screen.airdefense.map.sirens"},
						{"screen.airdefense.map.plan.pause", "screen.airdefense.map.plan.pause_on", "screen.airdefense.map.plan.pause_mp"},
						{"screen.airdefense.map.plan.open_short", "screen.airdefense.map.plan.back"}});
		for (int i = 0; i < cmd.length; i++) {
			final int k = i;
			cmd[i] = addRenderableWidget(UiButton.create(Component.empty(), b -> plannerPressed(k)).bounds(px0 + 4, my1 - 130 + 22 * i, pw, 20).build());
		}
		int y = my1 - 130;
		row[0] = addRenderableWidget(UiButton.create(Component.literal("<"), b -> cycle(0, -1)).bounds(px0 + 4, y, 18, 20).build());
		row[1] = addRenderableWidget(UiButton.create(Component.empty(), b -> cycle(0, 1)).bounds(px0 + 24, y, pw - 40, 20).build());
		row[2] = addRenderableWidget(UiButton.create(Component.literal(">"), b -> cycle(0, 1)).bounds(px0 + pw - 14, y, 18, 20).build());
		if (!initialised) {
			initialised = true;
			Player p = minecraft.player;
			if (p != null) {
				centerX = p.getX();
				centerZ = p.getZ();
				ItemStack tablet = DesignatorItem.held(p);
				target = tablet != null ? DesignatorItem.getTarget(tablet) : null;
			}
			MapClient.send(MapActionPayload.refresh());
		}
		updateButtons();
	}

	@Override
	public void tick() {
		Player p = minecraft.player;
		if (p == null || DesignatorItem.held(p) == null) {
			onClose();
			return;
		}
		if (++refresh % 10 == 0) {
			MapClient.send(MapActionPayload.refresh());
		}
		if (follow) {
			centerX = p.getX();
			centerZ = p.getZ();
		}
		// The tablet's stored target (with the real ground height filled in by the server) wins over the local one,
		// once the server has had time to take a change made here.
		if (paused()) {
			// The server stands still: the tablet's copy of the target cannot catch up yet.
			localChange = 20;
		} else if (localChange > 0) {
			localChange--;
		} else {
			target = DesignatorItem.getTarget(DesignatorItem.held(p));
		}
		updateButtons();
	}

	// ------------------------------------------------------------------------------------------------
	// Lite: the two sides

	private static String sideKey() {
		return MapClient.side() == com.stasdoto.airdefense.map.Sides.ENEMY ? "screen.airdefense.map.side.enemy" : "screen.airdefense.map.side.ours";
	}

	/** Play the other side: the list, the orders and what is put down next are that side's. */
	public void toggleSide() {
		MapClient.playSide(MapClient.side() == com.stasdoto.airdefense.map.Sides.ENEMY ? com.stasdoto.airdefense.map.Sides.OURS
				: com.stasdoto.airdefense.map.Sides.ENEMY);
		selected = -1;
		plan.clear();
		picked.clear();
		updateButtons();
	}

	private void updateButtons() {
		if (fireButton == null) {
			return;
		}
		sideButton.setMessage(Component.translatable(sideKey()));
		sideButton.setStyle(MapClient.side() == com.stasdoto.airdefense.map.Sides.ENEMY ? UiButton.Style.DANGER : UiButton.Style.PRIMARY);
		pauseButton.active = canPause();
		pauseButton.setMessage(Component.translatable(!canPause() ? "screen.airdefense.map.plan.pause_mp" : paused
				? "screen.airdefense.map.plan.pause_on" : "screen.airdefense.map.plan.pause"));
		plannerButton.setMessage(Component.translatable(tab == 1 ? "screen.airdefense.map.plan.back" : "screen.airdefense.map.plan.open_short"));
		massButton.active = target != null;
		boolean planner = tab == 1;
		for (UiButton b : cmd) {
			b.visible = planner;
		}
		for (UiButton b : row) {
			b.visible = planner;
		}
		if (planner) {
			for (UiButton b : new UiButton[]{fireButton, modeButton, clearButton, meButton, planButton, roundsButton}) {
				b.visible = false;
			}
			updatePlannerButtons();
			return;
		}
		clearButton.visible = true;
		meButton.visible = true;
		MapStatusPayload.Entry sel = selectedEntry();
		boolean arty = sel != null && typeOf(sel).isArtillery();
		boolean defense = sel != null && typeOf(sel).hasMode() && !arty;
		fireButton.visible = !defense;
		fireButton.active = sel != null && typeOf(sel).isLauncher() && cannotFire(sel) == null;
		boolean planable = sel != null && typeOf(sel).launcher != null && (typeOf(sel).launcher.missile.kind == MissileType.Kind.DRONE
				|| typeOf(sel).launcher.missile.kind == MissileType.Kind.CRUISE);
		int pw = PANEL_W - 8;
		fireButton.setWidth(planable || arty ? pw / 2 - 1 : pw);
		planButton.visible = planable;
		roundsButton.visible = arty;
		// Artillery: the counter-battery switch takes the "me" button's place.
		meButton.visible = !arty;
		modeButton.visible = defense || arty;
		modeButton.setX(arty ? px0 + 4 + pw / 2 + 1 : px0 + 4);
		modeButton.setY(arty ? my1 - 42 : my1 - 20);
		modeButton.setWidth(arty ? pw - pw / 2 - 1 : pw);
		if (arty) {
			modeButton.setMessage(Component.translatable(sel.mode() == VehicleEntity.MODE_AUTO ? "screen.airdefense.map.cb_on" : "screen.airdefense.map.cb_off"));
			roundsButton.setMessage(Component.translatable("screen.airdefense.map.rounds", rounds > 0 ? rounds : typeOf(sel).launcher.salvo));
		} else if (defense && typeOf(sel).isRadar()) {
			modeButton.setMessage(Component.translatable(sel.mode() == VehicleEntity.MODE_OFF ? "screen.airdefense.map.radar_off" : "screen.airdefense.map.radar_on"));
		} else if (defense) {
			modeButton.setMessage(Component.translatable(switch (sel.mode()) {
				case VehicleEntity.MODE_AUTO -> "screen.airdefense.map.mode_auto";
				case VehicleEntity.MODE_MANUAL -> "screen.airdefense.map.mode_manual";
				default -> "screen.airdefense.map.mode_off";
			}));
		}
		clearButton.active = target != null;
		meButton.active = !follow;
	}

	/** 0 = the vehicles, 1 = the strike planner. */
	public void setTab(int tab) {
		this.tab = tab;
		listScroll = 0;
		updateButtons();
	}


	@Override
	public void onClose() {
		MapCache.save();
		super.onClose();
	}

	// ------------------------------------------------------------------------------------------------
	// Coordinates

	private float scale() {
		return ZOOMS[zoom];
	}

	private double mapCenterX() {
		return (mx0 + mx1) / 2.0;
	}

	private double mapCenterY() {
		return (my0 + my1) / 2.0;
	}

	public double toScreenX(double worldX) {
		return mapCenterX() + (worldX - centerX) * scale();
	}

	public double toScreenY(double worldZ) {
		return mapCenterY() + (worldZ - centerZ) * scale();
	}

	private double toWorldX(double sx) {
		return centerX + (sx - mapCenterX()) / scale();
	}

	private double toWorldZ(double sy) {
		return centerZ + (sy - mapCenterY()) / scale();
	}

	private boolean inMap(double x, double y) {
		return x >= mx0 && x < mx1 && y >= my0 && y < my1;
	}

	// ------------------------------------------------------------------------------------------------
	// Vehicles

	private static List<MapStatusPayload.Entry> entries() {
		return MapClient.vehicles();
	}

	@Nullable
	private MapStatusPayload.Entry selectedEntry() {
		for (MapStatusPayload.Entry e : entries()) {
			if (e.id() == selected) {
				return e;
			}
		}
		return null;
	}

	private static VehicleType typeOf(MapStatusPayload.Entry e) {
		return VehicleType.byId(e.type());
	}

	private double distanceToTarget(MapStatusPayload.Entry e) {
		if (target == null) {
			return -1;
		}
		return Math.sqrt(Mth.square(target.getX() + 0.5 - e.x()) + Mth.square(target.getZ() + 0.5 - e.z()));
	}

	/** Why the selected launcher cannot fire right now, or null if it can. */
	@Nullable
	private Component cannotFire(MapStatusPayload.Entry e) {
		VehicleType type = typeOf(e);
		if (type.launcher == null) {
			return null;
		}
		if (target == null) {
			return Component.translatable("screen.airdefense.map.no_target");
		}
		double d = distanceToTarget(e);
		if (d < minRange(type)) {
			return Component.translatable("screen.airdefense.map.too_close", (int) d);
		}
		if (d > type.launcher.maxRange) {
			return Component.translatable("screen.airdefense.map.out_of_range", (int) d, type.launcher.maxRange);
		}
		if (e.firing()) {
			return Component.translatable("screen.airdefense.map.st.firing");
		}
		if (e.busy() > 0) {
			return Component.translatable("screen.airdefense.map.st.reload", e.busy() / 20 + 1);
		}
		if (ready(e) == 0) {
			return Component.translatable("screen.airdefense.map.st.empty");
		}
		return null;
	}

	/** Closer than this the launcher (or gun) cannot fire. */
	private static int minRange(VehicleType type) {
		return type.launcher == null ? VehicleEntity.MIN_STRIKE_DISTANCE : Math.max(VehicleEntity.MIN_STRIKE_DISTANCE, type.launcher.minRange);
	}

	/** Missiles on the rails, or the gun's rounds. */
	private static int ready(MapStatusPayload.Entry e) {
		return VehicleType.byId(e.type()).isArtillery() ? Math.max(0, e.ammo()) : Integer.bitCount(e.loaded());
	}

	private Component statusLine(MapStatusPayload.Entry e) {
		VehicleType type = typeOf(e);
		Component s;
		if (type.isLauncher()) {
			if (e.firing()) {
				s = Component.translatable("screen.airdefense.map.st.firing");
			} else if (e.busy() > 0) {
				s = Component.translatable("screen.airdefense.map.st.reload", e.busy() / 20 + 1);
			} else if (ready(e) == 0) {
				s = Component.translatable("screen.airdefense.map.st.empty");
			} else {
				s = Component.translatable(type.isArtillery() ? "screen.airdefense.map.st.ready_rounds" : "screen.airdefense.map.st.ready", ready(e));
			}
			if (type.isArtillery() && e.mode() == VehicleEntity.MODE_AUTO) {
				s = Component.empty().append(s).append(Component.translatable("screen.airdefense.map.st.cb"));
			}
		} else if (e.mode() == VehicleEntity.MODE_OFF) {
			s = Component.translatable("screen.airdefense.map.st.off");
		} else if (type.isRadar()) {
			s = Component.translatable(e.state() == VehicleEntity.DEPLOYED ? type.isJammer() ? "screen.airdefense.map.st.ew_on" : "screen.airdefense.map.st.radar_on"
					: "screen.airdefense.map.st.march");
		} else if (e.mode() == VehicleEntity.MODE_MANUAL) {
			s = Component.translatable("screen.airdefense.map.st.manual", Math.max(0, e.ammo()), type.magazine());
		} else if (e.busy() > 0) {
			s = Component.translatable("screen.airdefense.map.st.reload", e.busy() / 20 + 1);
		} else if (e.state() != VehicleEntity.DEPLOYED) {
			s = Component.translatable("screen.airdefense.map.st.march");
		} else if (e.tracked() >= 0) {
			s = Component.translatable("screen.airdefense.map.st.tracking", Math.max(0, e.ammo()), type.magazine());
		} else {
			s = Component.translatable("screen.airdefense.map.st.duty", Math.max(0, e.ammo()), type.magazine());
		}
		if (e.reserve() >= 0) {
			s = Component.empty().append(s).append(Component.translatable("screen.airdefense.map.st.reserve", e.reserve()));
		}
		if (e.health() < 100) {
			s = Component.empty().append(s).append(Component.translatable("screen.airdefense.map.st.armour", e.health()));
		}
		return s;
	}

	/** The next choice of how many rounds the selected gun fires. */
	private void cycleRounds() {
		MapStatusPayload.Entry sel = selectedEntry();
		if (sel == null || !typeOf(sel).isArtillery()) {
			return;
		}
		int[] choices = typeOf(sel).launcher.gun() ? GUN_ROUNDS : ROCKET_ROUNDS;
		int k = 0;
		for (int i = 0; i < choices.length; i++) {
			if (choices[i] == rounds) {
				k = i;
			}
		}
		rounds = choices[(k + 1) % choices.length];
		updateButtons();
	}

	// ------------------------------------------------------------------------------------------------
	// Orders

	private void fire() {
		MapStatusPayload.Entry sel = selectedEntry();
		if (sel == null || target == null || cannotFire(sel) != null) {
			return;
		}
		int action = typeOf(sel).isArtillery() ? MapActionPayload.fireMission(rounds) : MapActionPayload.STRIKE;
		MapClient.send(new MapActionPayload(action, sel.id(), target.getX(), target.getY(), target.getZ()));
	}

	/** The flight task window for the selected drone or cruise missile launcher. */
	private void openPlan() {
		MapStatusPayload.Entry sel = selectedEntry();
		if (sel != null) {
			minecraft.gui.setScreen(new com.stasdoto.airdefense.client.drone.FlightScreen(sel, target));
		}
	}

	/** Every launcher in reach fires everything at the marked target. */
	private void massStrike() {
		if (target != null) {
			MapClient.send(new MapActionPayload(MapActionPayload.MASS_STRIKE, -1, target.getX(), target.getY(), target.getZ()));
		}
	}

	private void toggleMode() {
		MapStatusPayload.Entry sel = selectedEntry();
		if (sel == null || !typeOf(sel).hasMode()) {
			return;
		}
		int mode = typeOf(sel).isRadar() || typeOf(sel).isArtillery() ? (sel.mode() == VehicleEntity.MODE_OFF ? VehicleEntity.MODE_AUTO : VehicleEntity.MODE_OFF)
				: VehicleEntity.nextMode(sel.mode());
		MapClient.send(new MapActionPayload(MapActionPayload.SET_MODE, sel.id(), mode, 0, 0));
	}

	private void setTarget(int x, int z) {
		target = new BlockPos(x, MapActionPayload.Y_UNKNOWN, z);
		localChange = 20;
		MapClient.send(new MapActionPayload(MapActionPayload.SET_TARGET, -1, x, MapActionPayload.Y_UNKNOWN, z));
		updateButtons();
	}

	private void clearTarget() {
		target = null;
		localChange = 20;
		MapClient.send(new MapActionPayload(MapActionPayload.CLEAR_TARGET, -1, 0, 0, 0));
		updateButtons();
	}

	public void select(int vehicleId) {
		if (vehicleId != selected) {
			rounds = 0;
		}
		selected = vehicleId;
		updateButtons();
	}

	// ------------------------------------------------------------------------------------------------
	// For the tests

	/** For the automated test: a click on the map at this world point (sets the target point). */
	public void pickPoint(int x, int z) {
		setTarget(x, z);
	}

	/** For the automated test: the map point that a screen position shows. */
	public int[] worldAt(double sx, double sy) {
		return new int[]{Mth.floor(toWorldX(sx)), Mth.floor(toWorldZ(sy))};
	}

	public void centerOn(double x, double z, int zoomIndex) {
		follow = false;
		centerX = x;
		centerZ = z;
		zoom = Mth.clamp(zoomIndex, 0, ZOOMS.length - 1);
	}

	// ------------------------------------------------------------------------------------------------
	// Input

	@Nullable
	private MapStatusPayload.Entry vehicleAt(double sx, double sy) {
		MapStatusPayload.Entry best = null;
		double bestD = 8 * 8;
		for (MapStatusPayload.Entry e : entries()) {
			double d = Mth.square(toScreenX(e.x()) - sx) + Mth.square(toScreenY(e.z()) - sy);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double x = event.x();
		double y = event.y();
		if (tab == 1 && event.button() == InputConstants.MOUSE_BUTTON_LEFT && plannerListClick(x, y)) {
			return true;
		}
		if (inMap(x, y)) {
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				clearTarget();
				return true;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				pressedInMap = true;
				dragMoved = false;
				pressX = x;
				pressY = y;
				return true;
			}
		}
		if (tab == 0 && x >= px0 && x < px0 + PANEL_W && y >= listTop && y < listBottom && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int row = (int) ((y - listTop) / ROW_H) + listScroll;
			List<MapStatusPayload.Entry> list = entries();
			if (row >= 0 && row < list.size()) {
				MapStatusPayload.Entry e = list.get(row);
				select(e.id());
				follow = false;
				centerX = e.x();
				centerZ = e.z();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (pressedInMap) {
			if (!dragMoved && Math.hypot(event.x() - pressX, event.y() - pressY) > 3) {
				dragMoved = true;
			}
			if (dragMoved) {
				follow = false;
				centerX -= dx / scale();
				centerZ -= dy / scale();
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressedInMap && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			pressedInMap = false;
			if (!dragMoved && tab == 1) {
				plannerClick(Mth.floor(toWorldX(event.x())), Mth.floor(toWorldZ(event.y())));
				return true;
			}
			if (!dragMoved) {
				MapStatusPayload.Entry hit = vehicleAt(event.x(), event.y());
				if (hit != null) {
					select(hit.id());
				} else {
					setTarget(Mth.floor(toWorldX(event.x())), Mth.floor(toWorldZ(event.y())));
				}
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (inMap(x, y) && scrollY != 0) {
			double wx = toWorldX(x);
			double wz = toWorldZ(y);
			zoom = Mth.clamp(zoom + (scrollY > 0 ? -1 : 1), 0, ZOOMS.length - 1);
			follow = false;
			centerX = wx - (x - mapCenterX()) / scale();
			centerZ = wz - (y - mapCenterY()) / scale();
			return true;
		}
		if (tab == 0 && x >= px0 && y >= listTop && y < listBottom && scrollY != 0) {
			int rows = Math.max(1, (listBottom - listTop) / ROW_H);
			int size = entries().size();
			listScroll = Mth.clamp(listScroll - (int) Math.signum(scrollY), 0, Math.max(0, size - rows));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k == InputConstants.KEY_EQUALS) {
			zoom = Math.max(0, zoom - 1);
			return true;
		}
		if (k == InputConstants.KEY_MINUS) {
			zoom = Math.min(ZOOMS.length - 1, zoom + 1);
			return true;
		}
		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------------------------------------
	// Drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xE0101114);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long f0 = System.nanoTime();
		drawAll(g, mouseX, mouseY, partialTick);
		frameNanos = System.nanoTime() - f0;
		maxFrameNanos = Math.max(maxFrameNanos, frameNanos);
	}

	/** For the tests: how long the last frame's drawing took, and the longest since the screen opened (ns). */
	public static long frameNanos;
	public static long maxFrameNanos;

	private void drawAll(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		com.stasdoto.airdefense.client.ui.Ui.shadow(g, mx0 - 2, my0 - 2, mx1 + 2, my1 + 2);
		com.stasdoto.airdefense.client.ui.Ui.round(g, mx0 - 2, my0 - 2, mx1 + 2, my1 + 2, 3, com.stasdoto.airdefense.client.ui.Ui.LINE);
		g.enableScissor(mx0, my0, mx1, my1);
		g.fill(mx0, my0, mx1, my1, 0xFF14181C);
		drawAtlasLand(g);
		if (scale() >= 0.125f) {
			drawTerrain(g);
		}
		drawGrid(g);
		drawRanges(g);
		drawFires(g);
		drawRecon(g);
		drawTarget(g);
		drawMissiles(g, partialTick);
		drawVehicles(g);
		if (tab == 1) {
			drawPlanMap(g);
		}
		drawPlayer(g, partialTick);
		g.disableScissor();
		drawScaleBar(g);
		drawHeader(g, mouseX, mouseY);
		drawPanel(g, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	// ------------------------------------------------------------------------------------------------
	// The atlas (1.25): the land of the whole 10 km, the borders, the towns' outlines, the roads

	private void drawAtlasLand(GuiGraphicsExtractor g) {
		if (!AtlasClient.loaded || AtlasClient.texture() == null) {
			return;
		}
		int span = AtlasClient.size * AtlasClient.res;
		int tw = AtlasClient.size * AtlasClient.UP;
		double ax0 = toScreenX(AtlasClient.x0);
		double ay0 = toScreenY(AtlasClient.z0);
		double ax1 = toScreenX(AtlasClient.x0 + span);
		double ay1 = toScreenY(AtlasClient.z0 + span);
		// Only the part on screen (a quad tens of thousands of pixels across would be wasted).
		double cx0 = Math.max(mx0, ax0);
		double cy0 = Math.max(my0, ay0);
		double cx1 = Math.min(mx1, ax1);
		double cy1 = Math.min(my1, ay1);
		if (cx1 <= cx0 || cy1 <= cy0) {
			return;
		}
		double k = tw / (ax1 - ax0);
		float u = (float) ((cx0 - ax0) * k);
		float v = (float) ((cy0 - ay0) * k);
		int w = (int) Math.ceil(cx1 - cx0);
		int h = (int) Math.ceil(cy1 - cy0);
		int uw = Math.max(1, (int) Math.round(w * k));
		int vh = Math.max(1, (int) Math.round(h * k));
		g.blit(RenderPipelines.GUI_TEXTURED, AtlasClient.texture(), (int) Math.floor(cx0), (int) Math.floor(cy0), u, v, w, h, uw, vh, tw, tw);
	}

	private void drawTerrain(GuiGraphicsExtractor g) {
		int rx0 = MapCache.regionIndex(Mth.floor(toWorldX(mx0)));
		int rx1 = MapCache.regionIndex(Mth.floor(toWorldX(mx1)));
		int rz0 = MapCache.regionIndex(Mth.floor(toWorldZ(my0)));
		int rz1 = MapCache.regionIndex(Mth.floor(toWorldZ(my1)));
		for (int rx = rx0; rx <= rx1; rx++) {
			for (int rz = rz0; rz <= rz1; rz++) {
				MapCache.Region r = MapCache.region(rx, rz);
				if (r == null) {
					continue;
				}
				int sx0 = Mth.floor(toScreenX(rx * (double) MapCache.REGION));
				int sx1 = Mth.floor(toScreenX((rx + 1) * (double) MapCache.REGION));
				int sy0 = Mth.floor(toScreenY(rz * (double) MapCache.REGION));
				int sy1 = Mth.floor(toScreenY((rz + 1) * (double) MapCache.REGION));
				g.blit(RenderPipelines.GUI_TEXTURED, r.texture(), sx0, sy0, 0f, 0f, sx1 - sx0, sy1 - sy0,
						MapCache.REGION, MapCache.REGION, MapCache.REGION, MapCache.REGION);
			}
		}
	}

	/** Grid step in blocks: lines at least ~40 px apart. */
	private int gridStep() {
		int step = 16;
		while (step * scale() < 40) {
			step *= 2;
		}
		return step;
	}

	/**
	 * The military grid: 100-block squares named "Д-14" (letter west to east, number north to south); zoomed in, the
	 * nine small squares of each ("улитка", 1-9); zoomed far out, only every fifth line.
	 */
	private void drawGrid(GuiGraphicsExtractor g) {
		double px = Grid.SIZE * scale();
		int every = px >= 26 ? 1 : px >= 6 ? 5 : 10;
		int step = Grid.SIZE * every;
		int x0 = Mth.floor(toWorldX(mx0) / step) * step;
		int z0 = Mth.floor(toWorldZ(my0) / step) * step;
		boolean cyr = MapClient.cyrillic();
		if (px >= 150) {
			// The small squares.
			int sub = Grid.SIZE / 3;
			for (int x = x0; toScreenX(x) < mx1; x += Grid.SIZE) {
				for (int k = 1; k <= 2; k++) {
					int sx = Mth.floor(toScreenX(x + (k == 1 ? sub : Grid.SIZE - sub)));
					for (int y = my0; y < my1; y += 4) {
						g.fill(sx, y, sx + 1, y + 2, 0x18FFFFFF);
					}
				}
			}
			for (int z = z0; toScreenY(z) < my1; z += Grid.SIZE) {
				for (int k = 1; k <= 2; k++) {
					int sy = Mth.floor(toScreenY(z + (k == 1 ? sub : Grid.SIZE - sub)));
					for (int x = mx0; x < mx1; x += 4) {
						g.fill(x, sy, x + 2, sy + 1, 0x18FFFFFF);
					}
				}
			}
		}
		for (int x = x0; toScreenX(x) < mx1; x += step) {
			int sx = Mth.floor(toScreenX(x));
			g.fill(sx, my0, sx + 1, my1, x == 0 ? 0x60FFFFFF : 0x30FFFFFF);
		}
		for (int z = z0; toScreenY(z) < my1; z += step) {
			int sy = Mth.floor(toScreenY(z));
			g.fill(mx0, sy, mx1, sy + 1, z == 0 ? 0x60FFFFFF : 0x30FFFFFF);
		}
		// Square names in their top left corners.
		if (px * every >= 34) {
			for (int x = x0; toScreenX(x) < mx1; x += step) {
				for (int z = z0; toScreenY(z) < my1; z += step) {
					int sx = Mth.floor(toScreenX(x)) + 2;
					int sy = Mth.floor(toScreenY(z)) + 2;
					if (sx >= mx0 && sy >= my0) {
						small(g, Grid.square(x, z, cyr), sx, sy, 0x90FFFFFF);
					}
				}
			}
		}
	}

	private void drawRanges(GuiGraphicsExtractor g) {
		MapStatusPayload.Entry sel = selectedEntry();
		for (MapStatusPayload.Entry e : entries()) {
			VehicleType type = typeOf(e);
			if (type.radar != null) {
				boolean on = e.state() == VehicleEntity.DEPLOYED;
				// 1.34: a jammer's reach in violet.
				int rgb = type.isJammer() ? 0xB98CFF : 0x5FE07A;
				int color = e == sel ? 0xC0000000 | rgb : on ? 0x50000000 | rgb : 0x30808080;
				circle(g, toScreenX(e.x()), toScreenY(e.z()), type.radar.range * scale(), color, e == sel ? 0 : 5);
			} else if (type.defense != null) {
				boolean on = e.mode() != VehicleEntity.MODE_OFF;
				int color = e == sel ? 0xC04FB8E8 : on ? 0x554FB8E8 : 0x30808080;
				circle(g, toScreenX(e.x()), toScreenY(e.z()), type.defense.range * scale(), color, e == sel ? 0 : 3);
			} else if (type.launcher != null && e == sel) {
				circle(g, toScreenX(e.x()), toScreenY(e.z()), type.launcher.maxRange * scale(), 0xB0E8A33C, 4);
				circle(g, toScreenX(e.x()), toScreenY(e.z()), minRange(type) * scale(), 0x80E8A33C, 2);
			}
			// Where a launcher is aiming right now.
			if (type.launcher != null && e.hasTarget() && (e.firing() || e == sel)) {
				line(g, toScreenX(e.x()), toScreenY(e.z()), toScreenX(e.tx() + 0.5), toScreenY(e.tz() + 0.5), 0xC0FF7A3A, 3);
			}
		}
	}

	/**
	 * 1.30: enemy firing positions found by the counter-battery radars - a red cross in a circle with how long ago
	 * they fired; they fade out over five minutes.
	 */
	private void drawFires(GuiGraphicsExtractor g) {
		for (MapStatusPayload.Fire f : MapClient.fires()) {
			double sx = toScreenX(f.x() + 0.5);
			double sy = toScreenY(f.z() + 0.5);
			if (sx < mx0 - 10 || sx > mx1 + 10 || sy < my0 - 10 || sy > my1 + 10) {
				continue;
			}
			int a = (int) (255 * Math.max(0.35, 1 - f.age() / 300.0));
			int c = (a << 24) | 0xFF3B30;
			int x = (int) sx;
			int y = (int) sy;
			for (int k = -4; k <= 4; k++) {
				g.fill(x + k, y + k, x + k + 1, y + k + 1, c);
				g.fill(x + k, y - k, x + k + 1, y - k + 1, c);
			}
			circle(g, sx, sy, 7, c, 0);
			if (f.age() < 20 && (System.currentTimeMillis() / 300) % 2 == 0) {
				circle(g, sx, sy, 10, c, 0);
			}
			String age = f.age() < 60 ? f.age() + Component.translatable("screen.airdefense.map.sec").getString()
					: f.age() / 60 + Component.translatable("screen.airdefense.map.min").getString();
			small(g, Component.translatable("screen.airdefense.map.fire_pos", age).getString(), x + 10, y - 3, c);
		}
	}

	/**
	 * 1.34: the player's reconnaissance drones (a small cyan cross, the ground they see round it) and what they have
	 * seen - enemy vehicles as red diamonds with their names, soldiers as red dots; it fades out over a minute.
	 */
	private void drawRecon(GuiGraphicsExtractor g) {
		for (MapStatusPayload.Eye e : MapClient.eyes()) {
			double sx = toScreenX(e.x() + 0.5);
			double sy = toScreenY(e.z() + 0.5);
			circle(g, sx, sy, e.range() * scale(), 0x7046D8FF, 3);
			int x = (int) sx;
			int y = (int) sy;
			g.fill(x - 5, y, x + 6, y + 1, 0xFF46D8FF);
			g.fill(x, y - 2, x + 1, y + 4, 0xFF46D8FF);
			g.fill(x - 2, y + 3, x + 3, y + 4, 0xFF46D8FF);
			small(g, Component.translatable("radar.airdefense.contact." + com.stasdoto.airdefense.missile.MissileType.byId(e.type()).name()
					.toLowerCase(java.util.Locale.ROOT)).getString(), x + 7, y - 3, 0xFF46D8FF);
		}
		for (MapStatusPayload.Spot s : MapClient.spots()) {
			double sx = toScreenX(s.x() + 0.5);
			double sy = toScreenY(s.z() + 0.5);
			if (sx < mx0 - 10 || sx > mx1 + 10 || sy < my0 - 10 || sy > my1 + 10) {
				continue;
			}
			int a = (int) (255 * Math.max(0.35, 1 - s.age() / 60.0));
			int c = (a << 24) | 0xFF4A3A;
			int x = (int) sx;
			int y = (int) sy;
			if (s.type() < 0) {
				g.fill(x - 1, y - 1, x + 2, y + 2, c);
				continue;
			}
			for (int k = 0; k <= 4; k++) {
				g.fill(x - 4 + k, y - k, x + 5 - k, y - k + 1, c);
				g.fill(x - 4 + k, y + k, x + 5 - k, y + k + 1, c);
			}
			if (scale() >= 0.25f) {
				small(g, Component.translatable("map.airdefense.short." + VehicleType.byId(s.type()).id).getString(), x + 6, y - 3, c);
			}
		}
	}

	private void drawTarget(GuiGraphicsExtractor g) {
		if (target == null) {
			return;
		}
		double sx = toScreenX(target.getX() + 0.5);
		double sy = toScreenY(target.getZ() + 0.5);
		MapStatusPayload.Entry sel = selectedEntry();
		if (sel != null && typeOf(sel).isLauncher()) {
			double d = distanceToTarget(sel);
			boolean ok = cannotFire(sel) == null || (d >= minRange(typeOf(sel)) && d <= typeOf(sel).launcher.maxRange);
			double ex = toScreenX(sel.x());
			double ey = toScreenY(sel.z());
			line(g, ex, ey, sx, sy, ok ? 0xE0FFD24A : 0xE0FF6A5A, 4);
			String label = (int) d + " " + Component.translatable("screen.airdefense.map.blocks").getString();
			g.text(font, label, (int) ((ex + sx) / 2) + 4, (int) ((ey + sy) / 2) - 4, ok ? 0xFFFFE07A : C_BAD);
		}
		int x = (int) sx;
		int y = (int) sy;
		int pulse = (int) (Math.sin(System.currentTimeMillis() / 160.0) * 1.5 + 6);
		g.fill(x - pulse - 3, y, x - 2, y + 1, C_TARGET);
		g.fill(x + 3, y, x + pulse + 4, y + 1, C_TARGET);
		g.fill(x, y - pulse - 3, x + 1, y - 2, C_TARGET);
		g.fill(x, y + 3, x + 1, y + pulse + 4, C_TARGET);
		circle(g, x + 0.5, y + 0.5, 4, C_TARGET, 0);
	}

	private void drawMissiles(GuiGraphicsExtractor g, float partialTick) {
		if (minecraft.level == null) {
			return;
		}
		for (Entity ent : minecraft.level.entitiesForRendering()) {
			if (!(ent instanceof MissileEntity m)) {
				continue;
			}
			double x = Mth.lerp(partialTick, m.xo, m.getX());
			double z = Mth.lerp(partialTick, m.zo, m.getZ());
			double sx = toScreenX(x);
			double sy = toScreenY(z);
			boolean threat = m.getMissileType().threat;
			int color = threat ? 0xFFFF3B30 : 0xFFF2F6FF;
			// A short tail: where it was a second ago.
			double vx = (m.getX() - m.xo) * 20;
			double vz = (m.getZ() - m.zo) * 20;
			line(g, sx, sy, sx - vx * scale(), sy - vz * scale(), threat ? 0x90FF3B30 : 0x90F2F6FF, 0);
			int r = threat ? 2 : 1;
			g.fill((int) sx - r, (int) sy - r, (int) sx + r + 1, (int) sy + r + 1, color);
		}
		// Which threat each battery is engaging.
		for (MapStatusPayload.Entry e : entries()) {
			if (e.tracked() >= 0 && minecraft.level.getEntity(e.tracked()) instanceof MissileEntity m) {
				line(g, toScreenX(e.x()), toScreenY(e.z()), toScreenX(m.getX()), toScreenY(m.getZ()), 0x704FB8E8, 2);
			}
		}
	}

	/** 2.0: the vehicle labels already drawn this frame (x0, y0, x1, y1) - the next one moves down or is left out. */
	private final java.util.List<int[]> labelRects = new java.util.ArrayList<>();

	private void drawVehicles(GuiGraphicsExtractor g) {
		labelRects.clear();
		for (MapStatusPayload.Entry e : entries()) {
			VehicleType type = typeOf(e);
			double sx = toScreenX(e.x());
			double sy = toScreenY(e.z());
			if (sx < mx0 - 20 || sx > mx1 + 20 || sy < my0 - 20 || sy > my1 + 20) {
				continue;
			}
			boolean sel = e.id() == selected;
			int color = type.isLauncher() ? C_LAUNCHER : type.isRadar() ? C_RADAR : C_DEFENSE;
			if (type.hasMode() && !type.isArtillery() && e.mode() == VehicleEntity.MODE_OFF) {
				color = 0xFF8A949C;
			}
			int len = Math.max(8, (int) (type.geometry.length() * scale()));
			int wid = Math.max(5, (int) (type.geometry.width() * scale()));
			g.pose().pushMatrix();
			g.pose().translate((float) sx, (float) sy);
			g.pose().rotate((float) Math.toRadians(e.yaw()));
			if (sel) {
				g.fill(-wid / 2 - 2, -len / 2 - 2, wid - wid / 2 + 2, len - len / 2 + 2, 0xFFFFFFFF);
			}
			g.fill(-wid / 2 - 1, -len / 2 - 1, wid - wid / 2 + 1, len - len / 2 + 1, 0xFF101418);
			g.fill(-wid / 2, -len / 2, wid - wid / 2, len - len / 2, color);
			// The nose (cab) is darker so you can see where it faces.
			g.fill(-wid / 2, len - len / 2 - Math.max(2, len / 4), wid - wid / 2, len - len / 2, 0xFF20282E);
			g.pose().popMatrix();
			String label = Component.translatable("map.airdefense.short." + type.id).getString();
			int lx = (int) sx + Math.max(len, wid) / 2 + 3;
			int ly = (int) sy - 3;
			int lw = (int) (font.width(label) * 0.75f);
			boolean place = false;
			for (int tries = 0; tries < 4 && !place; tries++) {
				place = true;
				for (int[] r : labelRects) {
					if (lx < r[2] && lx + lw > r[0] && ly < r[3] && ly + 7 > r[1]) {
						place = false;
						ly = r[3] + 1;
						break;
					}
				}
			}
			if (place || sel) {
				labelRects.add(new int[]{lx, ly, lx + lw, ly + 7});
				small(g, label, lx, ly, sel ? 0xFFFFFFFF : 0xFFE6E9EC);
			}
			if (e.firing()) {
				int blink = (System.currentTimeMillis() / 250) % 2 == 0 ? 0xFFFF5A3A : 0xFFFFD24A;
				circle(g, sx, sy, Math.max(len, wid) * 0.8 + 3, blink, 0);
			}
		}
	}


	private void drawPlayer(GuiGraphicsExtractor g, float partialTick) {
		Player p = minecraft.player;
		if (p == null) {
			return;
		}
		double sx = toScreenX(Mth.lerp(partialTick, p.xo, p.getX()));
		double sy = toScreenY(Mth.lerp(partialTick, p.zo, p.getZ()));
		g.pose().pushMatrix();
		g.pose().translate((float) sx, (float) sy);
		g.pose().rotate((float) Math.toRadians(p.getYRot()));
		// A white arrow pointing where the player looks.
		g.fill(-1, -3, 2, 4, 0xFF101418);
		g.fill(-3, 1, 4, 3, 0xFF101418);
		g.fill(0, -2, 1, 5, 0xFFFFFFFF);
		g.fill(-2, 2, 3, 3, 0xFFFFFFFF);
		g.fill(-1, 3, 2, 4, 0xFFFFFFFF);
		g.pose().popMatrix();
	}

	private void drawScaleBar(GuiGraphicsExtractor g) {
		int step = gridStep();
		int px = (int) (step * scale());
		int x = mx0 + 6;
		int y = my1 - 8;
		g.fill(x - 2, y - 11, x + px + 40, y + 4, 0xA0101418);
		g.fill(x, y, x + px, y + 2, 0xFFFFFFFF);
		g.fill(x, y - 3, x + 1, y + 2, 0xFFFFFFFF);
		g.fill(x + px - 1, y - 3, x + px, y + 2, 0xFFFFFFFF);
		small(g, step + " " + Component.translatable("screen.airdefense.map.blocks").getString(), x, y - 9, 0xFFFFFFFF);
	}

	/**
	 * 2.0: the header's buttons side by side after the title, each as wide as its longest label (in the player's
	 * language), squeezed evenly when the screen is narrow - none over another or over the side panel.
	 */
	private void layoutHeader(UiButton[] buttons, String[][] keys) {
		int x = mx0 + (int) (font.width(title) * 1.25f) + 8;
		int right = width - 4;
		int gap = 3;
		int[] w = new int[buttons.length];
		int total = 0;
		for (int i = 0; i < buttons.length; i++) {
			int m = 30;
			for (String k : keys[i]) {
				m = Math.max(m, font.width(Component.translatable(k)) + 12);
			}
			w[i] = m;
			total += m + gap;
		}
		float k = total > right - x ? (right - x) / (float) total : 1f;
		for (int i = 0; i < buttons.length; i++) {
			int bw = Math.max(24, (int) (w[i] * k));
			buttons[i].setX(x);
			buttons[i].setY(2);
			buttons[i].setWidth(bw);
			x += bw + gap;
		}
		headerEnd = x;
	}

	/** Where the header's buttons end (the coordinates go under the map when there is no room for them up there). */
	private int headerEnd;

	private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		com.stasdoto.airdefense.client.ui.Ui.big(g, font, title, mx0, 3, 1.25f, com.stasdoto.airdefense.client.ui.Ui.TEXT);
		String info;
		if (inMap(mouseX, mouseY)) {
			double wx = toWorldX(mouseX);
			double wz = toWorldZ(mouseY);
			info = String.format(Locale.ROOT, "%s   X %d   Z %d", Grid.full(wx, wz, MapClient.cyrillic()), Mth.floor(wx), Mth.floor(wz));
		} else {
			info = "";
		}
		Component hint = Component.translatable("screen.airdefense.map.hint");
		if (mx1 - font.width(info) > headerEnd + 4) {
			g.text(font, info, mx1 - font.width(info), 5, C_DIM);
			small(g, hint.getString(), mx0, height - 11, C_DIM);
		} else {
			// No room up top: the coordinates at the bottom right of the map, the hint left of them.
			g.text(font, info, mx1 - font.width(info), height - 12, C_DIM);
			if (info.isEmpty()) {
				small(g, hint.getString(), mx0, height - 11, C_DIM);
			}
		}
	}

	private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x0 = px0;
		int x1 = px0 + PANEL_W;
		com.stasdoto.airdefense.client.ui.Ui.panel(g, x0, my0 - 2, x1, my1 + 2);
		if (tab == 1) {
			drawPlanPanel(g, my0 + 3);
			return;
		}
		List<MapStatusPayload.Entry> list = entries();
		if (list.isEmpty()) {
			g.textWithWordWrap(font, Component.translatable("screen.airdefense.map.none"), x0 + 5, listTop + 4, PANEL_W - 10, C_DIM);
		}
		int rows = Math.max(1, (listBottom - listTop) / ROW_H);
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - rows));
		Player p = minecraft.player;
		for (int i = 0; i < rows && i + listScroll < list.size(); i++) {
			MapStatusPayload.Entry e = list.get(i + listScroll);
			VehicleType type = typeOf(e);
			int y = listTop + i * ROW_H;
			boolean sel = e.id() == selected;
			boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW_H;
			if (sel || hover) {
				com.stasdoto.airdefense.client.ui.Ui.card(g, x0 + 3, y, x1 - 3, y + ROW_H - 1, !sel, sel);
			}
			g.fill(x0 + 5, y + 4, x0 + 9, y + 8, type.isLauncher() ? C_LAUNCHER : type.isRadar() ? C_RADAR : C_DEFENSE);
			Component name = Component.translatable("entity.airdefense." + type.id);
			g.text(font, name, x0 + 12, y + 1, sel ? 0xFFFFFFFF : C_TEXT);
			if (p != null) {
				String dist = (int) Math.sqrt(Mth.square(e.x() - p.getX()) + Mth.square(e.z() - p.getZ())) + "";
				small(g, dist, x1 - 6 - (int) (font.width(dist) * 0.75f), y + 2, C_DIM);
			}
			small(g, statusLine(e).getString(), x0 + 12, y + 11, e.firing() ? 0xFFFF9A4A : C_DIM);
		}
		if (list.size() > rows) {
			small(g, (listScroll + 1) + "-" + Math.min(list.size(), listScroll + rows) + " / " + list.size(), x0 + 5, listBottom - 2, C_DIM);
		}
		// Selected vehicle: what the next press of the button will do.
		com.stasdoto.airdefense.client.ui.Ui.divider(g, x0 + 2, x1 - 2, my1 - 68);
		MapStatusPayload.Entry sel = selectedEntry();
		Component l1;
		Component l2 = null;
		int c2 = C_DIM;
		if (sel == null) {
			l1 = Component.translatable("screen.airdefense.map.select");
		} else if (typeOf(sel).isLauncher()) {
			l1 = target == null ? Component.translatable("screen.airdefense.map.no_target")
					: Component.translatable("screen.airdefense.map.target", target.getX(), target.getZ());
			Component why = cannotFire(sel);
			if (target != null) {
				l2 = why != null ? why : Component.translatable("screen.airdefense.map.range", (int) distanceToTarget(sel), typeOf(sel).launcher.maxRange);
				c2 = why != null ? C_BAD : 0xFF8AE07A;
			}
		} else if (typeOf(sel).isRadar()) {
			l1 = Component.translatable("entity.airdefense." + typeOf(sel).id);
			l2 = Component.translatable(typeOf(sel).isJammer() ? "screen.airdefense.map.ew_range" : "screen.airdefense.map.radar_range",
					(int) typeOf(sel).radar.range);
		} else {
			l1 = Component.translatable("entity.airdefense." + typeOf(sel).id);
			l2 = Component.translatable("screen.airdefense.map.ad_range", (int) typeOf(sel).defense.range);
		}
		small(g, l1.getString(), x0 + 5, my1 - 64, C_TEXT);
		if (l2 != null) {
			small(g, l2.getString(), x0 + 5, my1 - 55, c2);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// 2.0: command any country - put up its air defence, launchers and radars, strike with it, war and peace

	/** For the tests: press one of the planner's six buttons (0 at the top). */
	public void pressCommand(int k) {
		plannerPressed(k);
		updateButtons();
	}

	/** For the tests: turn the planner's salvo chooser. */
	public void cycleForTest(int r, int d) {
		cycle(r, d);
	}

	/** One line of the command panel, cut to its width; returns where the next one goes. */
	private int cmdLine(GuiGraphicsExtractor g, String text, int y, int color) {
		int max = (int) ((PANEL_W - 12) / 0.75f);
		if (font.width(text) > max) {
			while (!text.isEmpty() && font.width(text + "…") > max) {
				text = text.substring(0, text.length() - 1);
			}
			text = text + "…";
		}
		small(g, text, px0 + 6, y, color);
		return y + 9;
	}

	// ------------------------------------------------------------------------------------------------
	// 2.0: the pause and the strike planner - a massed strike planned calmly: each launcher its point, then all at once

	/** The game stands still while the map is open (singleplayer only). */
	private boolean paused;
	/** The plan: by launcher (its entity id) its point and salvo {x, z, salvo}; 0 = its usual salvo. */
	private final java.util.LinkedHashMap<Integer, int[]> plan = new java.util.LinkedHashMap<>();
	/** Launchers picked in the planner's list (the next point on the map goes to them). */
	private final java.util.Set<Integer> picked = new java.util.HashSet<>();
	private int planSalvo;
	private static final int[] PLAN_SALVOS = {0, 1, 2, 4, 8};
	private int planScroll;
	/** Where the planner's list is on screen (set while it is drawn), and how many rows it shows. */
	private int planTop = -1;
	private int planRows;
	private UiButton pauseButton;
	private UiButton plannerButton;

	/** The side's launchers (rows of the planner): missiles, rockets and guns - not the drone stations. */
	private static List<MapStatusPayload.Entry> launchers() {
		List<MapStatusPayload.Entry> out = new ArrayList<>();
		for (MapStatusPayload.Entry e : entries()) {
			VehicleType t = typeOf(e);
			if (t.isLauncher() && !t.isDroneLauncher()) {
				out.add(e);
			}
		}
		return out;
	}

	/** The planner's buttons. */
	private void updatePlannerButtons() {
		row[1].setMessage(Component.translatable(planSalvo == 0 ? "screen.airdefense.map.plan.salvo_usual" : "screen.airdefense.map.plan.salvo",
				PLAN_SALVOS[planSalvo]));
		for (UiButton b : cmd) {
			b.setStyle(UiButton.Style.SECONDARY);
			b.active = true;
		}
		cmd[0].visible = false;
		cmd[1].setMessage(Component.translatable("screen.airdefense.map.plan.all_one"));
		cmd[1].active = target != null && !launchers().isEmpty();
		cmd[2].setMessage(Component.translatable("screen.airdefense.map.plan.clear"));
		cmd[2].active = !plan.isEmpty();
		cmd[3].setMessage(Component.translatable("screen.airdefense.map.plan.launch", plan.size()));
		cmd[3].setStyle(UiButton.Style.DANGER);
		cmd[3].active = !plan.isEmpty();
		cmd[4].setMessage(Component.translatable(picked.isEmpty() ? "screen.airdefense.map.plan.pick_all" : "screen.airdefense.map.plan.pick_none"));
		cmd[4].active = !launchers().isEmpty();
		cmd[5].setMessage(Component.translatable("screen.airdefense.map.plan.back"));
	}

	private void cycle(int r, int d) {
		planSalvo = Math.floorMod(planSalvo + d, PLAN_SALVOS.length);
		for (int k : picked) {
			int[] a = plan.get(k);
			if (a != null) {
				a[2] = PLAN_SALVOS[planSalvo];
			}
		}
		updateButtons();
	}

	private void plannerPressed(int k) {
		List<MapStatusPayload.Entry> list = launchers();
		switch (k) {
			case 1 -> {
				// Every picked launcher (all of them when none is picked) at the one point.
				if (target != null) {
					for (MapStatusPayload.Entry e : list) {
						if (picked.isEmpty() || picked.contains(e.id())) {
							plan.put(e.id(), new int[]{target.getX(), target.getZ(), PLAN_SALVOS[planSalvo]});
						}
					}
				}
			}
			case 2 -> plan.clear();
			case 3 -> launchPlan();
			case 4 -> {
				if (picked.isEmpty()) {
					for (MapStatusPayload.Entry e : list) {
						picked.add(e.id());
					}
				} else {
					picked.clear();
				}
			}
			case 5 -> setTab(0);
			default -> {
			}
		}
	}

	/** "Launch": the plan goes to the server, all at once; the game goes on. */
	private void launchPlan() {
		if (plan.isEmpty()) {
			return;
		}
		List<com.stasdoto.airdefense.map.StrikePlanPayload.Shot> shots = new ArrayList<>();
		plan.forEach((k, v) -> shots.add(new com.stasdoto.airdefense.map.StrikePlanPayload.Shot(k, v[0], v[1], v[2])));
		paused = false;
		if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.stasdoto.airdefense.map.StrikePlanPayload.TYPE)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.stasdoto.airdefense.map.StrikePlanPayload(shots));
		}
		plan.clear();
		picked.clear();
	}

	/** For the tests: pick a planner row (by its place in the list) on or off. */
	public void pickLauncherForTest(int row) {
		List<MapStatusPayload.Entry> list = launchers();
		if (row >= 0 && row < list.size()) {
			int k = list.get(row).id();
			if (!picked.remove(k)) {
				picked.add(k);
			}
		}
		updateButtons();
	}

	/** A click on the map in the planner: the point, and it goes to the picked launchers. */
	private void plannerClick(int x, int z) {
		setTarget(x, z);
		for (MapStatusPayload.Entry e : launchers()) {
			if (picked.contains(e.id())) {
				plan.put(e.id(), new int[]{x, z, PLAN_SALVOS[planSalvo]});
			}
		}
		updateButtons();
	}

	/** The plan on the map: a line from each launcher to its point, the flight time half-way, the point. */
	private void drawPlanMap(GuiGraphicsExtractor g) {
		for (MapStatusPayload.Entry u : launchers()) {
			int[] a = plan.get(u.id());
			boolean sel = picked.contains(u.id());
			double sx = toScreenX(u.x());
			double sy = toScreenY(u.z());
			if (sel) {
				circle(g, sx, sy, 6, 0xFFFFFFFF, 0);
			}
			if (a == null) {
				continue;
			}
			VehicleType t = typeOf(u);
			double tx = toScreenX(a[0] + 0.5);
			double ty = toScreenY(a[1] + 0.5);
			double d = Math.hypot(a[0] - u.x(), a[1] - u.z());
			boolean ok = d <= t.launcher.maxRange && d >= minRange(t);
			line(g, sx, sy, tx, ty, ok ? 0xE0FFB040 : 0xE0FF5A4A, 3);
			int x = (int) tx;
			int y = (int) ty;
			g.fill(x - 3, y, x + 4, y + 1, C_TARGET);
			g.fill(x, y - 3, x + 1, y + 4, C_TARGET);
			String label = ok ? "~" + flightSeconds(t, d) + Component.translatable("screen.airdefense.map.sec").getString()
					: Component.translatable("screen.airdefense.map.plan.out").getString();
			small(g, label, (int) ((sx + tx) / 2) + 3, (int) ((sy + ty) / 2) - 3, ok ? 0xFFFFE07A : C_BAD);
		}
	}

	/** The planner's panel: the launchers, each with its rounds, its point and the flight time. */
	private void drawPlanPanel(GuiGraphicsExtractor g, int y) {
		y = cmdLine(g, Component.translatable("screen.airdefense.map.plan.title").getString(), y, C_TEXT);
		y = cmdLine(g, Component.translatable(paused() ? "screen.airdefense.map.plan.paused" : "screen.airdefense.map.plan.hint").getString(), y,
				paused() ? com.stasdoto.airdefense.client.ui.Ui.GOLD : C_DIM);
		List<MapStatusPayload.Entry> list = launchers();
		planTop = y;
		planRows = Math.max(1, (my1 - 134 - y) / 10);
		planScroll = Mth.clamp(planScroll, 0, Math.max(0, list.size() - planRows));
		if (list.isEmpty()) {
			cmdLine(g, Component.translatable("screen.airdefense.map.plan.none").getString(), y, C_DIM);
			return;
		}
		for (int i = 0; i < planRows && i + planScroll < list.size(); i++) {
			MapStatusPayload.Entry u = list.get(i + planScroll);
			VehicleType t = typeOf(u);
			int[] a = plan.get(u.id());
			boolean sel = picked.contains(u.id());
			int ry = y + i * 10;
			if (sel) {
				g.fill(px0 + 3, ry - 1, px0 + PANEL_W - 3, ry + 8, 0x40FFFFFF);
			}
			g.fill(px0 + 6, ry + 2, px0 + 9, ry + 5, C_LAUNCHER);
			String what = Component.translatable("map.airdefense.short." + t.id).getString() + " " + ready(u);
			String where;
			if (a == null) {
				where = "—";
			} else {
				double d = Math.hypot(a[0] - u.x(), a[1] - u.z());
				where = (d <= t.launcher.maxRange ? "→ ~" + flightSeconds(t, d) + Component.translatable("screen.airdefense.map.sec").getString()
						: Component.translatable("screen.airdefense.map.plan.out").getString()) + (a[2] > 0 ? " ×" + a[2] : "");
			}
			small(g, what, px0 + 12, ry, sel ? 0xFFFFFFFF : C_TEXT);
			small(g, where, px0 + PANEL_W - 6 - (int) (font.width(where) * 0.75f), ry, a == null ? C_DIM : 0xFFFFE07A);
		}
	}

	/** Pausing works in the player's own world (not opened to others). */
	private boolean canPause() {
		return minecraft != null && minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null
				&& !minecraft.getSingleplayerServer().isPublished();
	}

	public boolean paused() {
		return paused && canPause();
	}

	@Override
	public boolean isPauseScreen() {
		return paused();
	}

	/** For the tests, and the button: the pause on or off. */
	public void setPaused(boolean on) {
		paused = on && canPause();
		updateButtons();
	}

	/** For the tests: open the planner. */
	public void plannerForTest() {
		setTab(1);
	}

	/** For the tests: launchers in the planner's list, and in the plan. */
	public int[] plannerCounts() {
		return new int[]{launchers().size(), plan.size()};
	}

	/** A click on the planner's list: that launcher picked or dropped. */
	private boolean plannerListClick(double x, double y) {
		if (planTop < 0 || x < px0 || x >= px0 + PANEL_W || y < planTop || y >= planTop + planRows * 10) {
			return false;
		}
		int row = (int) ((y - planTop) / 10) + planScroll;
		pickLauncherForTest(row);
		return true;
	}

	/** Seconds a round of this launcher takes to fly that far (roughly). */
	private static int flightSeconds(VehicleType t, double d) {
		MissileType m = t.launcher.missile;
		double speed = Math.max(0.3, m.maxSpeed * 0.85);
		double arc = m.kind == MissileType.Kind.BALLISTIC || m.artillery() ? 1.35 : 1.08;
		return (int) Math.ceil(d * arc / speed / 20.0);
	}

	// ------------------------------------------------------------------------------------------------
	// Primitives

	private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		// 2.0: in the side panel a long line is made smaller, then cut short, so it never runs past the panel's edge.
		float scale = 0.75f;
		if (x >= px0 && x < px0 + PANEL_W) {
			int room = px0 + PANEL_W - 5 - x;
			int w = font.width(text);
			if (w * scale > room) {
				scale = Math.max(0.6f, room / (float) w);
				if (w * scale > room) {
					String cut = text;
					while (cut.length() > 1 && (font.width(cut) + font.width("…")) * scale > room) {
						cut = cut.substring(0, cut.length() - 1);
					}
					text = cut + "…";
				}
			}
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	/** A dotted ({@code dash} > 0) or solid line of single pixels, clipped to the map. */
	private void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color, int dash) {
		GuiDraw.line(g, GuiDraw.Clip.rect(mx0, my0, mx1, my1), x0, y0, x1, y1, 1f, color, dash);
	}

	private void circle(GuiGraphicsExtractor g, double cx, double cy, double r, int color, int dash) {
		GuiDraw.circle(g, GuiDraw.Clip.rect(mx0, my0, mx1, my1), cx, cy, r, 1f, color, dash);
	}

	// ------------------------------------------------------------------------------------------------
	// For the automated test

	public List<int[]> vehicleScreenPositions() {
		List<int[]> out = new ArrayList<>();
		for (MapStatusPayload.Entry e : entries()) {
			out.add(new int[]{e.id(), (int) toScreenX(e.x()), (int) toScreenY(e.z())});
		}
		return out;
	}
}
