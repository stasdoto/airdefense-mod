package com.stasdoto.airdefense.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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

import com.stasdoto.airdefense.client.nation.NationClient;
import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.nation.NationActionPayload;
import com.stasdoto.airdefense.nation.NationMapPayload;
import com.stasdoto.airdefense.nation.Settlement;
import com.stasdoto.airdefense.nation.SoldierEntity;
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
	private static final int C_TEXT = 0xFFE6E9EC;
	private static final int C_DIM = 0xFF9AA4AE;
	private static final int C_BAD = 0xFFFF7A6A;

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

	private Button fireButton;
	private Button modeButton;
	private Button clearButton;
	private Button meButton;
	/** Panel tab: 0 = vehicles, 1 = villages and the army. */
	private int tab;
	private int selectedVillage = -1;
	private Button tabVehicles;
	private Button tabArmy;
	private Button callButton;
	private Button sendButton;
	private Button homeButton;
	private Button dismissButton;
	private Button manageButton;
	private Button planButton;
	private Button massButton;
	/** A building picked on the map (army tab): to pull down or rebuild. */
	private int pickedVillage = -1;
	private int pickedIndex = -1;
	private int pickedType;
	private int rebuildChoice;
	private Button prevTypeButton;
	private Button typeButton;
	private Button nextTypeButton;
	private Button rebuildButton;
	private Button demolishButton;
	private Button dropButton;

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
		tabVehicles = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.tab_vehicles"), b -> setTab(0))
				.bounds(px0 + 4, my0 + 1, pw / 2 - 1, 13).build());
		tabArmy = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.tab_army"), b -> setTab(1))
				.bounds(px0 + 4 + pw / 2 + 1, my0 + 1, pw - pw / 2 - 1, 13).build());
		manageButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.manage"), b -> army(NationActionPayload.OPEN))
				.bounds(px0 + 4, my1 - 64, pw, 20).build());
		callButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.call"), b -> army(NationActionPayload.MOBILIZE))
				.bounds(px0 + 4, my1 - 42, pw / 2 - 1, 20).build());
		dismissButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.dismiss"), b -> army(NationActionPayload.DEMOBILIZE))
				.bounds(px0 + 4 + pw / 2 + 1, my1 - 42, pw - pw / 2 - 1, 20).build());
		sendButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.send"), b -> army(NationActionPayload.ORDER))
				.bounds(px0 + 4, my1 - 20, pw / 2 - 1, 20).build());
		homeButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.home"), b -> army(NationActionPayload.RECALL))
				.bounds(px0 + 4 + pw / 2 + 1, my1 - 20, pw - pw / 2 - 1, 20).build());
		fireButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.fire"), b -> fire())
				.bounds(px0 + 4, my1 - 20, pw, 20).build());
		modeButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.mode_auto"), b -> toggleMode())
				.bounds(px0 + 4, my1 - 20, pw, 20).build());
		clearButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.clear"), b -> clearTarget())
				.bounds(px0 + 4, my1 - 42, pw / 2 - 1, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.to_radar"), b -> minecraft.gui.setScreen(new RadarScreen()))
				.bounds(mx0 + font.width(title) + 8, 2, 70, 13).build());
		massButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.mass_strike"), b -> massStrike())
				.bounds(mx0 + font.width(title) + 82, 2, 110, 13).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.sirens"),
						b -> minecraft.gui.setScreen(new com.stasdoto.airdefense.client.siren.SirenScreen()))
				.bounds(mx0 + font.width(title) + 196, 2, 80, 13).build());
		planButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.plan"), b -> openPlan())
				.bounds(px0 + 4 + pw / 2 + 1, my1 - 20, pw - pw / 2 - 1, 20).build());
		meButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.me"), b -> follow = true)
				.bounds(px0 + 4 + pw / 2 + 1, my1 - 42, pw - pw / 2 - 1, 20).build());
		prevTypeButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleRebuild(-1)).bounds(px0 + 4, my1 - 64, 18, 20).build());
		typeButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleRebuild(1)).bounds(px0 + 24, my1 - 64, pw - 40, 20).build());
		nextTypeButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleRebuild(1)).bounds(px0 + pw - 14, my1 - 64, 18, 20).build());
		rebuildButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.rebuild"), b -> buildingAction(NationActionPayload.REBUILD))
				.bounds(px0 + 4, my1 - 42, pw, 20).build());
		demolishButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.demolish"), b -> buildingAction(NationActionPayload.DEMOLISH))
				.bounds(px0 + 4, my1 - 20, pw / 2 - 1, 20).build());
		dropButton = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.map.drop"), b -> dropBuilding())
				.bounds(px0 + 4 + pw / 2 + 1, my1 - 20, pw - pw / 2 - 1, 20).build());
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
	public boolean isPauseScreen() {
		return false;
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
		if (localChange > 0) {
			localChange--;
		} else {
			target = DesignatorItem.getTarget(DesignatorItem.held(p));
		}
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
		if (d < VehicleEntity.MIN_STRIKE_DISTANCE) {
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
		if (e.loaded() == 0) {
			return Component.translatable("screen.airdefense.map.st.empty");
		}
		return null;
	}

	private Component statusLine(MapStatusPayload.Entry e) {
		VehicleType type = typeOf(e);
		Component s;
		if (type.isLauncher()) {
			if (e.firing()) {
				s = Component.translatable("screen.airdefense.map.st.firing");
			} else if (e.busy() > 0) {
				s = Component.translatable("screen.airdefense.map.st.reload", e.busy() / 20 + 1);
			} else if (e.loaded() == 0) {
				s = Component.translatable("screen.airdefense.map.st.empty");
			} else {
				s = Component.translatable("screen.airdefense.map.st.ready", Integer.bitCount(e.loaded()));
			}
		} else if (e.mode() == VehicleEntity.MODE_OFF) {
			s = Component.translatable("screen.airdefense.map.st.off");
		} else if (type.isRadar()) {
			s = Component.translatable(e.state() == VehicleEntity.DEPLOYED ? "screen.airdefense.map.st.radar_on" : "screen.airdefense.map.st.march");
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

	private void updateButtons() {
		if (fireButton == null) {
			return;
		}
		boolean army = tab == 1;
		tabVehicles.active = army;
		tabArmy.active = !army;
		NationMapPayload.Village v = selectedVillage();
		boolean mine = v != null && v.mine();
		boolean editing = army && pickedVillage >= 0;
		prevTypeButton.visible = editing;
		typeButton.visible = editing;
		nextTypeButton.visible = editing;
		rebuildButton.visible = editing;
		demolishButton.visible = editing;
		dropButton.visible = editing;
		if (editing) {
			typeButton.setMessage(Component.translatable(REBUILD_TYPES[rebuildChoice].key()));
			rebuildButton.active = mine && REBUILD_TYPES[rebuildChoice].ordinal() != pickedType;
			demolishButton.active = mine;
		}
		army = army && !editing;
		callButton.visible = army;
		dismissButton.visible = army;
		manageButton.visible = army;
		manageButton.active = mine;
		sendButton.visible = army;
		homeButton.visible = army;
		callButton.active = mine;
		dismissButton.active = mine && v.soldiers() > 0;
		sendButton.active = mine && v.soldiers() > 0 && target != null;
		homeButton.active = mine && v.soldiers() > 0;
		clearButton.visible = !army;
		meButton.visible = !army;
		massButton.active = target != null;
		if (army || editing) {
			fireButton.visible = false;
			modeButton.visible = false;
			planButton.visible = false;
			return;
		}
		MapStatusPayload.Entry sel = selectedEntry();
		boolean defense = sel != null && typeOf(sel).hasMode();
		fireButton.visible = !defense;
		fireButton.active = sel != null && typeOf(sel).isLauncher() && cannotFire(sel) == null;
		boolean planable = sel != null && typeOf(sel).launcher != null && (typeOf(sel).launcher.missile.kind == MissileType.Kind.DRONE
				|| typeOf(sel).launcher.missile.kind == MissileType.Kind.CRUISE);
		int pw = PANEL_W - 8;
		fireButton.setWidth(planable ? pw / 2 - 1 : pw);
		planButton.visible = planable;
		modeButton.visible = defense;
		if (defense && typeOf(sel).isRadar()) {
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

	// ------------------------------------------------------------------------------------------------
	// Orders

	private void fire() {
		MapStatusPayload.Entry sel = selectedEntry();
		if (sel == null || target == null || cannotFire(sel) != null) {
			return;
		}
		MapClient.send(new MapActionPayload(MapActionPayload.STRIKE, sel.id(), target.getX(), target.getY(), target.getZ()));
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
		int mode = typeOf(sel).isRadar() ? (sel.mode() == VehicleEntity.MODE_OFF ? VehicleEntity.MODE_AUTO : VehicleEntity.MODE_OFF)
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
		selected = vehicleId;
		updateButtons();
	}

	// ------------------------------------------------------------------------------------------------
	// Villages and the army

	/** For the automated test: a click on the map at this world point (sets the target point). */
	public void pickPoint(int x, int z) {
		setTarget(x, z);
	}

	public void setTab(int tab) {
		this.tab = tab;
		listScroll = 0;
		updateButtons();
	}

	private static List<NationMapPayload.Village> villages() {
		List<NationMapPayload.Village> list = new ArrayList<>(NationClient.villages());
		// Your own villages first.
		list.sort((a, b) -> Boolean.compare(b.mine(), a.mine()));
		return list;
	}

	@Nullable
	private NationMapPayload.Village selectedVillage() {
		for (NationMapPayload.Village v : NationClient.villages()) {
			if (v.id() == selectedVillage) {
				return v;
			}
		}
		return null;
	}

	public void selectVillage(int id) {
		selectedVillage = id;
		updateButtons();
	}

	@Nullable
	private NationMapPayload.Village villageAt(double sx, double sy) {
		NationMapPayload.Village best = null;
		double bestD = 10 * 10;
		for (NationMapPayload.Village v : NationClient.villages()) {
			double d = Mth.square(toScreenX(v.x()) - sx) + Mth.square(toScreenY(v.z()) - sy);
			if (d < bestD) {
				bestD = d;
				best = v;
			}
		}
		return best;
	}

	private void army(int action) {
		NationMapPayload.Village v = selectedVillage();
		if (v == null) {
			return;
		}
		if (action == NationActionPayload.ORDER) {
			if (target == null) {
				return;
			}
			NationClient.send(action, v.id(), 0, target.getX(), target.getY(), target.getZ());
		} else {
			NationClient.send(action, v.id(), 1, 0, 0, 0);
		}
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
		if (x >= px0 && x < px0 + PANEL_W && y >= listTop && y < listEnd() && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int row = (int) ((y - listTop) / ROW_H) + listScroll;
			if (tab == 1) {
				List<NationMapPayload.Village> villages = villages();
				if (row >= 0 && row < villages.size()) {
					NationMapPayload.Village v = villages.get(row);
					selectVillage(v.id());
					follow = false;
					centerX = v.x();
					centerZ = v.z();
					return true;
				}
				return false;
			}
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
			if (!dragMoved) {
				MapStatusPayload.Entry hit = tab == 0 ? vehicleAt(event.x(), event.y()) : null;
				NationMapPayload.Village village = tab == 1 ? villageAt(event.x(), event.y()) : null;
				int[] building = tab == 1 && village == null ? buildingAt(event.x(), event.y()) : null;
				if (building != null) {
					pickBuilding(building);
				} else if (village != null) {
					dropBuilding();
					selectVillage(village.id());
				} else if (hit != null) {
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
		if (x >= px0 && y >= listTop && y < listEnd() && scrollY != 0) {
			int rows = Math.max(1, (listEnd() - listTop) / ROW_H);
			int size = tab == 1 ? villages().size() : entries().size();
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
		g.fill(0, 0, width, height, 0xD00C1014);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(mx0 - 2, my0 - 2, mx1 + 2, my1 + 2, 0xFF3A4652);
		g.enableScissor(mx0, my0, mx1, my1);
		g.fill(mx0, my0, mx1, my1, 0xFF161B21);
		drawAtlasLand(g);
		if (scale() >= 0.125f) {
			drawTerrain(g);
		}
		drawTerritory(g);
		drawRoads(g);
		drawTowns(g);
		drawGrid(g);
		drawRanges(g);
		drawVillages(g);
		drawTarget(g);
		drawMissiles(g, partialTick);
		drawVehicles(g);
		drawMen(g);
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

	/** Cached owners of the screen's cells: the country (by colour) and the city region of each. */
	private int[] cellCountry;
	private int[] cellRegion;
	private int cellsW;
	private int cellsH;
	private double cellsCX = Double.NaN;
	private double cellsCZ;
	private int cellsZoom = -1;
	private int cellsVersion = -1;
	private static final int CELL_PX = 2;

	private void computeCells() {
		if (cellsZoom == zoom && cellsVersion == AtlasClient.version && cellsCX == centerX && cellsCZ == centerZ && cellCountry != null) {
			return;
		}
		cellsW = (mx1 - mx0 + CELL_PX - 1) / CELL_PX + 1;
		cellsH = (my1 - my0 + CELL_PX - 1) / CELL_PX + 1;
		cellCountry = new int[cellsW * cellsH];
		cellRegion = new int[cellsW * cellsH];
		for (int j = 0; j < cellsH; j++) {
			double wz = toWorldZ(my0 + j * CELL_PX + CELL_PX * 0.5);
			for (int i = 0; i < cellsW; i++) {
				double wx = toWorldX(mx0 + i * CELL_PX + CELL_PX * 0.5);
				int region = AtlasClient.regionAt(wx, wz);
				cellRegion[j * cellsW + i] = region;
				cellCountry[j * cellsW + i] = AtlasClient.countryOfCity(region);
			}
		}
		cellsZoom = zoom;
		cellsVersion = AtlasClient.version;
		cellsCX = centerX;
		cellsCZ = centerZ;
	}

	/** A country's colour on the map (a little different for each country, so two of one dye still look apart). */
	private static int countryColor(int country) {
		var c = AtlasClient.country(country);
		if (c == null) {
			return 0xFF9AA4AE;
		}
		int rgb = c.argb() & 0xFFFFFF;
		int shift = Math.floorMod(country * 37, 31) - 15;
		int r = Mth.clamp(((rgb >> 16) & 255) + shift, 0, 255);
		int gr = Mth.clamp(((rgb >> 8) & 255) - shift / 2, 0, 255);
		int b = Mth.clamp((rgb & 255) + shift / 2, 0, 255);
		return 0xFF000000 | r << 16 | gr << 8 | b;
	}

	/**
	 * The countries' land lightly tinted in their colours, their borders as bold lines (red against a country at war
	 * with you), and the line between two cities' regions inside a country dotted.
	 */
	private void drawTerritory(GuiGraphicsExtractor g) {
		if (!AtlasClient.loaded || AtlasClient.CITIES.isEmpty()) {
			return;
		}
		computeCells();
		// Tint: runs of one country along each row.
		for (int j = 0; j < cellsH; j++) {
			int y = my0 + j * CELL_PX;
			int i = 0;
			while (i < cellsW) {
				int c = cellCountry[j * cellsW + i];
				int k = i + 1;
				while (k < cellsW && cellCountry[j * cellsW + k] == c) {
					k++;
				}
				if (c >= 0) {
					g.fill(mx0 + i * CELL_PX, y, Math.min(mx1, mx0 + k * CELL_PX), Math.min(my1, y + CELL_PX), (countryColor(c) & 0xFFFFFF) | 0x26000000);
				}
				i = k;
			}
		}
		// Borders.
		for (int j = 0; j < cellsH; j++) {
			for (int i = 0; i < cellsW; i++) {
				int a = cellCountry[j * cellsW + i];
				int ra = cellRegion[j * cellsW + i];
				int x = mx0 + i * CELL_PX;
				int y = my0 + j * CELL_PX;
				if (i + 1 < cellsW) {
					int b = cellCountry[j * cellsW + i + 1];
					if (b != a) {
						border(g, x + CELL_PX - 1, y, 2, CELL_PX, a, b);
					} else if (cellRegion[j * cellsW + i + 1] != ra && ((i + j) & 3) < 2) {
						g.fill(x + CELL_PX - 1, y, x + CELL_PX, y + CELL_PX, 0x70FFFFFF);
					}
				}
				if (j + 1 < cellsH) {
					int b = cellCountry[(j + 1) * cellsW + i];
					if (b != a) {
						border(g, x, y + CELL_PX - 1, CELL_PX, 2, a, b);
					} else if (cellRegion[(j + 1) * cellsW + i] != ra && ((i + j) & 3) < 2) {
						g.fill(x, y + CELL_PX - 1, x + CELL_PX, y + CELL_PX, 0x70FFFFFF);
					}
				}
			}
		}
		// Country names over their capitals when zoomed far out.
		if (scale() <= 0.125f && AtlasClient.politics != null) {
			for (var t : AtlasClient.politics.towns()) {
				if (!t.capital()) {
					continue;
				}
				var c = AtlasClient.country(t.country());
				if (c == null) {
					continue;
				}
				String name = c.name().toUpperCase(Locale.ROOT);
				int sx = (int) toScreenX(t.x());
				int sy = (int) toScreenY(t.z()) - 16;
				if (sx > mx0 - 100 && sx < mx1 + 100 && sy > my0 && sy < my1) {
					int w = font.width(name);
					g.text(font, name, sx - w / 2, sy, (countryColor(c.id()) & 0xFFFFFF) | 0xFF000000 | 0x404040);
				}
			}
		}
	}

	private void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int a, int b) {
		boolean war = isWar(a) || isWar(b);
		int color = war ? 0xE0FF4030 : 0xD0202428;
		g.fill(Math.max(mx0, x), Math.max(my0, y), Math.min(mx1, x + w), Math.min(my1, y + h), color);
	}

	private static boolean isWar(int country) {
		var c = AtlasClient.country(country);
		return c != null && c.war();
	}

	/** Roads: highways wide and yellow with a dark edge, country roads thin and white. */
	private void drawRoads(GuiGraphicsExtractor g) {
		if (!AtlasClient.loaded) {
			return;
		}
		double wx0 = toWorldX(mx0);
		double wx1 = toWorldX(mx1);
		double wz0 = toWorldZ(my0);
		double wz1 = toWorldZ(my1);
		float sc = scale();
		for (int pass = 0; pass < 2; pass++) {
			for (AtlasClient.Road r : AtlasClient.ROADS) {
				if (r.maxX() < wx0 || r.minX() > wx1 || r.maxZ() < wz0 || r.minZ() > wz1) {
					continue;
				}
				if (!r.highway() && sc < 0.0625f) {
					continue;
				}
				double width = r.highway() ? Math.max(2, 15 * sc) : Math.max(1, 5 * sc);
				int color;
				if (pass == 0) {
					width += 2;
					color = 0xFF2A2420;
				} else {
					color = r.highway() ? 0xFFF2C94A : 0xFFE8E8E0;
				}
				int stepPts = sc < 0.1f ? 2 : 1;
				for (int i = 0; i + stepPts < r.xs().length; i += stepPts) {
					thick(g, toScreenX(r.xs()[i]), toScreenY(r.zs()[i]), toScreenX(r.xs()[i + stepPts]), toScreenY(r.zs()[i + stepPts]), width, color);
				}
			}
		}
	}

	private void thick(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, double width, int color) {
		if (Math.max(x0, x1) < mx0 - 4 || Math.min(x0, x1) > mx1 + 4 || Math.max(y0, y1) < my0 - 4 || Math.min(y0, y1) > my1 + 4) {
			return;
		}
		double len = Math.hypot(x1 - x0, y1 - y0);
		int n = (int) Math.max(1, Math.ceil(len / Math.max(1, width * 0.5)));
		int hw = (int) Math.max(0, Math.floor(width / 2));
		int ww = (int) Math.max(1, Math.round(width));
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			int x = (int) Math.floor(x0 + (x1 - x0) * t) - hw;
			int y = (int) Math.floor(y0 + (y1 - y0) * t) - hw;
			g.fill(Math.max(mx0, x), Math.max(my0, y), Math.min(mx1, x + ww), Math.min(my1, y + ww), color);
		}
	}

	/**
	 * The towns' outlines: a city is the blocks of its street plan (filled in its country's colour, the edge drawn),
	 * a hamlet the outline round its houses and fields. Names for those too far for the village list.
	 */
	private void drawTowns(GuiGraphicsExtractor g) {
		if (!AtlasClient.loaded) {
			return;
		}
		float sc = scale();
		java.util.Set<Integer> listed = new java.util.HashSet<>();
		for (NationMapPayload.Village v : NationClient.villages()) {
			listed.add(v.id());
		}
		for (AtlasClient.City c : AtlasClient.CITIES) {
			var town = AtlasClient.cityTown(c.key());
			int color = town == null ? 0xFFB0B8C0 : countryColor(town.country());
			boolean war = town != null && isWar(town.country());
			int n = c.n();
			if (toScreenX(c.gx()[n]) < mx0 || toScreenX(c.gx()[0]) > mx1 || toScreenY(c.gz()[n]) < my0 || toScreenY(c.gz()[0]) > my1) {
				continue;
			}
			int fill = (color & 0xFFFFFF) | (sc < 0.1f ? 0xC0000000 : 0x55000000);
			int edge = war ? 0xFFFF3A2A : 0xFF14181C;
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (!c.cellOn(i, j)) {
						continue;
					}
					int x0 = (int) Math.floor(toScreenX(c.gx()[i]));
					int x1 = (int) Math.floor(toScreenX(c.gx()[i + 1]));
					int y0 = (int) Math.floor(toScreenY(c.gz()[j]));
					int y1 = (int) Math.floor(toScreenY(c.gz()[j + 1]));
					g.fill(Math.max(mx0, x0), Math.max(my0, y0), Math.min(mx1, x1), Math.min(my1, y1), fill);
					if (!c.cellOn(i - 1, j)) {
						g.fill(Math.max(mx0, x0), Math.max(my0, y0), Math.min(mx1, x0 + 1), Math.min(my1, y1), edge);
					}
					if (!c.cellOn(i + 1, j)) {
						g.fill(Math.max(mx0, x1 - 1), Math.max(my0, y0), Math.min(mx1, x1), Math.min(my1, y1), edge);
					}
					if (!c.cellOn(i, j - 1)) {
						g.fill(Math.max(mx0, x0), Math.max(my0, y0), Math.min(mx1, x1), Math.min(my1, y0 + 1), edge);
					}
					if (!c.cellOn(i, j + 1)) {
						g.fill(Math.max(mx0, x0), Math.max(my0, y1 - 1), Math.min(mx1, x1), Math.min(my1, y1), edge);
					}
				}
			}
			if (town != null && !listed.contains(town.id())) {
				townName(g, town, c.x(), c.z(), c.index() == 0 ? 0.75f : 0.45f);
			}
		}
		// Depots: the grey yard, the warehouses' roofs.
		for (AtlasClient.Depot d : AtlasClient.DEPOTS) {
			int x0 = (int) Math.floor(toScreenX(d.x0()));
			int y0 = (int) Math.floor(toScreenY(d.z0()));
			int x1 = (int) Math.ceil(toScreenX(d.x1() + 1));
			int y1 = (int) Math.ceil(toScreenY(d.z1() + 1));
			if (x1 < mx0 || x0 > mx1 || y1 < my0 || y0 > my1) {
				continue;
			}
			g.fill(Math.max(mx0, x0), Math.max(my0, y0), Math.min(mx1, x1), Math.min(my1, y1), 0xC05A5E62);
			for (int[] w : d.warehouses()) {
				int a = (int) Math.floor(toScreenX(w[0]));
				int b = (int) Math.floor(toScreenY(w[1]));
				int c = (int) Math.ceil(toScreenX(w[2] + 1));
				int e = (int) Math.ceil(toScreenY(w[3] + 1));
				g.fill(Math.max(mx0, a), Math.max(my0, b), Math.min(mx1, c), Math.min(my1, e), 0xFFC8CCD0);
				if (c - a > 3) {
					g.fill(Math.max(mx0, a), Math.max(my0, b), Math.min(mx1, c), Math.min(my1, b + 1), 0xFF3A3E42);
				}
			}
			if (sc >= 0.25f) {
				String label = Component.translatable("map.airdefense.depot").getString();
				small(g, label, (x0 + x1) / 2 - (int) (font.width(label) * 0.375f), y1 + 2, 0xFFD8DCE0);
			}
		}
		for (AtlasClient.Hamlet h : AtlasClient.HAMLETS) {
			double hx = toScreenX(h.x());
			double hy = toScreenY(h.z());
			if (hx < mx0 - 60 || hx > mx1 + 60 || hy < my0 - 60 || hy > my1 + 60) {
				continue;
			}
			var town = AtlasClient.hamletTown(h.key());
			int color = town == null ? 0xFFB0B8C0 : countryColor(town.country());
			boolean war = town != null && isWar(town.country());
			polygon(g, h.xs(), h.zs(), (color & 0xFFFFFF) | (sc < 0.1f ? 0xB0000000 : 0x50000000), war ? 0xFFFF3A2A : 0xFF14181C);
			if (town != null && !listed.contains(town.id())) {
				townName(g, town, h.x(), h.z(), 0.25f);
			}
		}
	}

	private void townName(GuiGraphicsExtractor g, com.stasdoto.airdefense.nation.AtlasPoliticsPayload.Town t, int x, int z, float minScale) {
		if (scale() < minScale * 0.25f && !t.capital()) {
			return;
		}
		String label = t.capital() ? "★ " + t.name() : t.name();
		int sx = (int) toScreenX(x);
		int sy = (int) toScreenY(z) + 4;
		if (sx > mx0 - 80 && sx < mx1 + 80 && sy > my0 && sy < my1) {
			small(g, label, sx - (int) (font.width(label) * 0.375f), sy, 0xFFF0F2F4);
		}
	}

	/** A filled polygon (by rows of the screen) with its outline. */
	private void polygon(GuiGraphicsExtractor g, int[] xs, int[] zs, int fill, int edge) {
		int n = xs.length;
		if (n < 3) {
			return;
		}
		double[] px = new double[n];
		double[] py = new double[n];
		double top = Double.MAX_VALUE;
		double bottom = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			px[i] = toScreenX(xs[i]);
			py[i] = toScreenY(zs[i]);
			top = Math.min(top, py[i]);
			bottom = Math.max(bottom, py[i]);
		}
		int y0 = (int) Math.max(my0, Math.floor(top));
		int y1 = (int) Math.min(my1 - 1, Math.ceil(bottom));
		double[] xsAt = new double[n];
		for (int y = y0; y <= y1; y++) {
			double yc = y + 0.5;
			int k = 0;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				if (py[i] <= yc && py[j] > yc || py[j] <= yc && py[i] > yc) {
					xsAt[k++] = px[i] + (yc - py[i]) / (py[j] - py[i]) * (px[j] - px[i]);
				}
			}
			java.util.Arrays.sort(xsAt, 0, k);
			for (int i = 0; i + 1 < k; i += 2) {
				int a = (int) Math.max(mx0, Math.round(xsAt[i]));
				int b = (int) Math.min(mx1, Math.round(xsAt[i + 1]));
				if (b > a) {
					g.fill(a, y, b, y + 1, fill);
				}
			}
		}
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			line(g, px[i], py[i], px[j], py[j], edge, 0);
		}
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
				int color = e == sel ? 0xC05FE07A : on ? 0x505FE07A : 0x30808080;
				circle(g, toScreenX(e.x()), toScreenY(e.z()), type.radar.range * scale(), color, e == sel ? 0 : 5);
			} else if (type.defense != null) {
				boolean on = e.mode() != VehicleEntity.MODE_OFF;
				int color = e == sel ? 0xC04FB8E8 : on ? 0x554FB8E8 : 0x30808080;
				circle(g, toScreenX(e.x()), toScreenY(e.z()), type.defense.range * scale(), color, e == sel ? 0 : 3);
			} else if (type.launcher != null && e == sel) {
				circle(g, toScreenX(e.x()), toScreenY(e.z()), type.launcher.maxRange * scale(), 0xB0E8A33C, 4);
				circle(g, toScreenX(e.x()), toScreenY(e.z()), VehicleEntity.MIN_STRIKE_DISTANCE * scale(), 0x80E8A33C, 2);
			}
			// Where a launcher is aiming right now.
			if (type.launcher != null && e.hasTarget() && (e.firing() || e == sel)) {
				line(g, toScreenX(e.x()), toScreenY(e.z()), toScreenX(e.tx() + 0.5), toScreenY(e.tz() + 0.5), 0xC0FF7A3A, 3);
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
			boolean ok = cannotFire(sel) == null || (d >= VehicleEntity.MIN_STRIKE_DISTANCE && d <= typeOf(sel).launcher.maxRange);
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

	private void drawVehicles(GuiGraphicsExtractor g) {
		for (MapStatusPayload.Entry e : entries()) {
			VehicleType type = typeOf(e);
			double sx = toScreenX(e.x());
			double sy = toScreenY(e.z());
			if (sx < mx0 - 20 || sx > mx1 + 20 || sy < my0 - 20 || sy > my1 + 20) {
				continue;
			}
			boolean sel = e.id() == selected;
			int color = type.isLauncher() ? C_LAUNCHER : type.isRadar() ? C_RADAR : C_DEFENSE;
			if (type.hasMode() && e.mode() == VehicleEntity.MODE_OFF) {
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
			small(g, label, (int) sx + Math.max(len, wid) / 2 + 3, (int) sy - 3, sel ? 0xFFFFFFFF : 0xFFE6E9EC);
			if (e.firing()) {
				int blink = (System.currentTimeMillis() / 250) % 2 == 0 ? 0xFFFF5A3A : 0xFFFFD24A;
				circle(g, sx, sy, Math.max(len, wid) * 0.8 + 3, blink, 0);
			}
		}
	}

	/** Villages: their area in the owner's colour (dotted for independent ones), the flag, the name. */
	private void drawVillages(GuiGraphicsExtractor g) {
		for (NationMapPayload.Village v : NationClient.villages()) {
			double sx = toScreenX(v.x());
			double sy = toScreenY(v.z());
			double r = v.radius() * scale();
			if (sx + r < mx0 || sx - r > mx1 || sy + r < my0 || sy - r > my1) {
				continue;
			}
			boolean independent = v.country().isEmpty();
			int color = v.color();
			boolean atlas = AtlasClient.outlines(v.id());
			if (atlas) {
				// Its outline comes from the atlas (drawTowns).
			} else if (v.half() > 0) {
				// A city: its square of streets.
				int h = (int) Math.round((v.half() + 2) * scale());
				int cx = (int) toScreenX(v.x() - 12);
				int cy = (int) toScreenY(v.z() - 11);
				int edge = (color & 0x00FFFFFF) | 0xC0000000;
				g.fill(cx - h, cy - h, cx + h + 1, cy + h + 1, (color & 0x00FFFFFF) | 0x28000000);
				g.fill(cx - h, cy - h, cx + h + 1, cy - h + 1, edge);
				g.fill(cx - h, cy + h, cx + h + 1, cy + h + 1, edge);
				g.fill(cx - h, cy - h, cx - h + 1, cy + h + 1, edge);
				g.fill(cx + h, cy - h, cx + h + 1, cy + h + 1, edge);
			} else {
				circle(g, sx, sy, r, (color & 0x00FFFFFF) | 0xC0000000, independent ? 3 : 0);
				if (!independent && r > 6) {
					circle(g, sx, sy, r - 1, (color & 0x00FFFFFF) | 0x60000000, 0);
				}
			}
			boolean sel = v.id() == selectedVillage;
			drawBuildings(g, v);
			// The flag at the town square.
			int fx = (int) toScreenX(v.fx());
			int fy = (int) toScreenY(v.fz());
			g.fill(fx, fy - 7, fx + 1, fy + 1, 0xFF101418);
			g.fill(fx + 1, fy - 7, fx + 6, fy - 3, color);
			if (v.war() && !atlas) {
				// At war with you: a red ring.
				circle(g, sx, sy, Math.max(5, r) + 1, 0xFFFF3A2A, 0);
				circle(g, sx, sy, Math.max(5, r) + 2, 0xFFFF3A2A, 0);
			}
			if (sel) {
				circle(g, sx, sy, Math.max(6, r) + 2, 0xFFFFFFFF, 2);
			}
			if (scale() >= 0.25f || sel || v.mine()) {
				String label = v.capital() ? "★ " + v.name() : v.name();
				small(g, label, (int) sx - (int) (font.width(label) * 0.375f), (int) sy + 4, sel ? 0xFFFFFFFF : 0xFFE6E9EC);
			}
		}
	}

	/** A village's buildings: their footprints, coloured by kind (outlined only while they are going up). */
	private void drawBuildings(GuiGraphicsExtractor g, NationMapPayload.Village v) {
		List<Integer> list = v.buildings();
		for (int i = 0; i + 2 < list.size(); i += 3) {
			int head = list.get(i);
			com.stasdoto.airdefense.nation.BuildingType type = com.stasdoto.airdefense.nation.BuildingType.byId(head >> 4);
			boolean done = (head & 1) == 1;
			int[] fp = footprint(v, list, i);
			int ax = fp[0];
			int az = fp[1];
			int bx = fp[2];
			int bz = fp[3];
			boolean picked = v.id() == pickedVillage && list.get(i + 2) == pickedIndex;
			int x0 = (int) Math.floor(toScreenX(Math.min(ax, bx)));
			int z0 = (int) Math.floor(toScreenY(Math.min(az, bz)));
			int x1 = (int) Math.ceil(toScreenX(Math.max(ax, bx) + 1));
			int z1 = (int) Math.ceil(toScreenY(Math.max(az, bz) + 1));
			if (x1 < mx0 || x0 > mx1 || z1 < my0 || z0 > my1) {
				continue;
			}
			x1 = Math.max(x1, x0 + 2);
			z1 = Math.max(z1, z0 + 2);
			int color = switch (type) {
				case SMALL_HOUSE, HOUSE -> 0xFF9A6A3E;
				case APARTMENTS -> 0xFFD8D8D2;
				case BARRACKS -> 0xFF5E6A3A;
				case HANGAR -> 0xFF8A8E92;
				case FACTORY -> 0xFFA04A36;
				case HOSPITAL -> 0xFFF2F2F2;
				case WAREHOUSE -> 0xFFC8A060;
				case PANEL5, PANEL9 -> 0xFFE4E0D6;
				case TOWER, OFFICE -> 0xFF6E90B0;
				case COTTAGE -> 0xFFB07A50;
				case SHOP -> 0xFFE0A040;
				case SCHOOL -> 0xFFD8B070;
				case CITY_HALL -> 0xFFF0EEE6;
				case PARK -> 0xFF4E9A3E;
				case GAS_STATION -> 0xFFE04040;
				case LOGISTICS_HUB -> 0xFF4A7AC0;
				case OIL_WELL, REFINERY -> 0xFF303030;
				case GARAGES -> 0xFF9A9A9A;
				case FARM -> 0xFFB04A3A;
				case FOOD_PLANT -> 0xFFC07040;
				case ARMS_FACTORY -> 0xFF5A6A40;
				case MARKET -> 0xFFE070B0;
				default -> 0xFF808080;
			};
			if (picked) {
				g.fill(x0 - 2, z0 - 2, x1 + 2, z1 + 2, 0xFFFFE040);
			}
			if (done) {
				g.fill(x0, z0, x1, z1, 0xFF101418);
				g.fill(x0 + 1, z0 + 1, x1 - 1, z1 - 1, color);
				if (type == com.stasdoto.airdefense.nation.BuildingType.HOSPITAL && x1 - x0 >= 5) {
					int cx = (x0 + x1) / 2;
					int cz = (z0 + z1) / 2;
					g.fill(cx - 1, cz - 2, cx + 1, cz + 2, 0xFFD02020);
					g.fill(cx - 2, cz - 1, cx + 2, cz + 1, 0xFFD02020);
				}
			} else {
				// Still going up: a dashed outline.
				for (int x = x0; x < x1; x += 2) {
					g.fill(x, z0, x + 1, z0 + 1, color);
					g.fill(x, z1 - 1, x + 1, z1, color);
				}
				for (int z = z0; z < z1; z += 2) {
					g.fill(x0, z, x0 + 1, z + 1, color);
					g.fill(x1 - 1, z, x1, z + 1, color);
				}
			}
		}
	}

	/** World corners {ax, az, bx, bz} of the building at {@code i} in the village's list. */
	private static int[] footprint(NationMapPayload.Village v, List<Integer> list, int i) {
		int head = list.get(i);
		int off = list.get(i + 1);
		com.stasdoto.airdefense.nation.BuildingType type = com.stasdoto.airdefense.nation.BuildingType.byId(head >> 4);
		net.minecraft.core.Direction facing = net.minecraft.core.Direction.from2DDataValue(head >> 1 & 3);
		int ox = v.x() + (off >> 16);
		int oz = v.z() + (short) (off & 0xFFFF);
		int hw = type.halfWidth();
		net.minecraft.core.Direction right = facing.getClockWise();
		// Two opposite corners of the footprint (local x from -hw to hw, z from 0 to depth - 1).
		return new int[]{ox + right.getStepX() * -hw, oz + right.getStepZ() * -hw,
				ox + right.getStepX() * hw + facing.getStepX() * (type.depth - 1), oz + right.getStepZ() * hw + facing.getStepZ() * (type.depth - 1)};
	}

	/** The building under the cursor: {village id, index in its list, type id}, or null. */
	@Nullable
	private int[] buildingAt(double sx, double sy) {
		double wx = toWorldX(sx);
		double wz = toWorldZ(sy);
		for (NationMapPayload.Village v : NationClient.villages()) {
			List<Integer> list = v.buildings();
			for (int i = 0; i + 2 < list.size(); i += 3) {
				int[] fp = footprint(v, list, i);
				if (wx >= Math.min(fp[0], fp[2]) && wx < Math.max(fp[0], fp[2]) + 1 && wz >= Math.min(fp[1], fp[3]) && wz < Math.max(fp[1], fp[3]) + 1) {
					return new int[]{v.id(), list.get(i + 2), list.get(i) >> 4};
				}
			}
		}
		return null;
	}

	/** Types a building can be rebuilt as. */
	private static final com.stasdoto.airdefense.nation.BuildingType[] REBUILD_TYPES = {
			com.stasdoto.airdefense.nation.BuildingType.COTTAGE, com.stasdoto.airdefense.nation.BuildingType.SMALL_HOUSE,
			com.stasdoto.airdefense.nation.BuildingType.HOUSE, com.stasdoto.airdefense.nation.BuildingType.APARTMENTS,
			com.stasdoto.airdefense.nation.BuildingType.PANEL5, com.stasdoto.airdefense.nation.BuildingType.PANEL9,
			com.stasdoto.airdefense.nation.BuildingType.TOWER, com.stasdoto.airdefense.nation.BuildingType.OFFICE,
			com.stasdoto.airdefense.nation.BuildingType.SHOP, com.stasdoto.airdefense.nation.BuildingType.SCHOOL,
			com.stasdoto.airdefense.nation.BuildingType.HOSPITAL, com.stasdoto.airdefense.nation.BuildingType.PARK,
			com.stasdoto.airdefense.nation.BuildingType.GARAGES, com.stasdoto.airdefense.nation.BuildingType.WAREHOUSE,
			com.stasdoto.airdefense.nation.BuildingType.GAS_STATION, com.stasdoto.airdefense.nation.BuildingType.LOGISTICS_HUB,
			com.stasdoto.airdefense.nation.BuildingType.REFINERY, com.stasdoto.airdefense.nation.BuildingType.BARRACKS,
			com.stasdoto.airdefense.nation.BuildingType.HANGAR, com.stasdoto.airdefense.nation.BuildingType.CITY_HALL};

	private void pickBuilding(int[] hit) {
		pickedVillage = hit[0];
		pickedIndex = hit[1];
		pickedType = hit[2];
		rebuildChoice = 0;
		for (int k = 0; k < REBUILD_TYPES.length; k++) {
			if (REBUILD_TYPES[k].ordinal() == hit[2]) {
				rebuildChoice = k;
			}
		}
		selectVillage(hit[0]);
	}

	/** For the tests: open the army tab and pick the building at a world point, to be rebuilt as {@code type}. */
	public boolean pickForTest(double wx, double wz, int type) {
		setTab(1);
		int[] hit = buildingAt(toScreenX(wx), toScreenY(wz));
		if (hit == null) {
			return false;
		}
		pickBuilding(hit);
		for (int k = 0; k < REBUILD_TYPES.length; k++) {
			if (REBUILD_TYPES[k].ordinal() == type) {
				rebuildChoice = k;
			}
		}
		updateButtons();
		return true;
	}

	private void dropBuilding() {
		pickedVillage = -1;
		pickedIndex = -1;
		updateButtons();
	}

	private void cycleRebuild(int d) {
		rebuildChoice = Math.floorMod(rebuildChoice + d, REBUILD_TYPES.length);
		updateButtons();
	}

	private void buildingAction(int action) {
		if (pickedVillage < 0) {
			return;
		}
		NationClient.send(action, pickedVillage, pickedIndex, REBUILD_TYPES[rebuildChoice].ordinal(), 0, 0);
		dropBuilding();
	}

	/** Guards, soldiers (yours with a white rim) and bandits (dark with a red rim). */
	private void drawMen(GuiGraphicsExtractor g) {
		for (NationMapPayload.Man m : NationClient.men()) {
			int sx = (int) toScreenX(m.x() + 0.5);
			int sy = (int) toScreenY(m.z() + 0.5);
			if (sx < mx0 || sx >= mx1 || sy < my0 || sy >= my1) {
				continue;
			}
			int rim = m.role() == SoldierEntity.BANDIT || m.role() == SoldierEntity.REBEL ? 0xFFFF3A2A : m.mine() ? 0xFFFFFFFF : 0xFF101418;
			g.fill(sx - 2, sy - 2, sx + 2, sy + 2, rim);
			g.fill(sx - 1, sy - 1, sx + 1, sy + 1, m.color());
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

	private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(font, title, mx0, 5, 0xFFFFD24A);
		String info;
		if (inMap(mouseX, mouseY)) {
			double wx = toWorldX(mouseX);
			double wz = toWorldZ(mouseY);
			info = String.format(Locale.ROOT, "%s   X %d   Z %d", Grid.full(wx, wz, MapClient.cyrillic()), Mth.floor(wx), Mth.floor(wz));
		} else {
			info = "";
		}
		g.text(font, info, mx1 - font.width(info), 5, C_DIM);
		Component hint = Component.translatable("screen.airdefense.map.hint");
		small(g, hint.getString(), mx0, height - 11, C_DIM);
	}

	private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x0 = px0;
		int x1 = px0 + PANEL_W;
		g.fill(x0, my0 - 2, x1, my1 + 2, 0xF0182028);
		g.fill(x0, my0 - 2, x1, my0 - 1, 0xFF3A4652);
		if (tab == 1) {
			drawArmyPanel(g, mouseX, mouseY);
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
				g.fill(x0 + 2, y, x1 - 2, y + ROW_H - 1, sel ? 0xFF2E3D4C : 0xFF222C36);
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
		g.fill(x0 + 2, my1 - 68, x1 - 2, my1 - 67, 0xFF3A4652);
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
			l2 = Component.translatable("screen.airdefense.map.radar_range", (int) typeOf(sel).radar.range);
		} else {
			l1 = Component.translatable("entity.airdefense." + typeOf(sel).id);
			l2 = Component.translatable("screen.airdefense.map.ad_range", (int) typeOf(sel).defense.range);
		}
		small(g, l1.getString(), x0 + 5, my1 - 64, C_TEXT);
		if (l2 != null) {
			small(g, l2.getString(), x0 + 5, my1 - 55, c2);
		}
	}

	private void drawArmyPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x0 = px0;
		int x1 = px0 + PANEL_W;
		List<NationMapPayload.Village> list = villages();
		if (list.isEmpty()) {
			g.textWithWordWrap(font, Component.translatable("screen.airdefense.map.no_villages"), x0 + 5, listTop + 4, PANEL_W - 10, C_DIM);
		}
		int rows = Math.max(1, (listEnd() - listTop) / ROW_H);
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - rows));
		for (int i = 0; i < rows && i + listScroll < list.size(); i++) {
			NationMapPayload.Village v = list.get(i + listScroll);
			int y = listTop + i * ROW_H;
			boolean sel = v.id() == selectedVillage;
			boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW_H;
			if (sel || hover) {
				g.fill(x0 + 2, y, x1 - 2, y + ROW_H - 1, sel ? 0xFF2E3D4C : 0xFF222C36);
			}
			g.fill(x0 + 5, y + 3, x0 + 10, y + 8, v.color());
			g.text(font, v.name(), x0 + 13, y + 1, sel ? 0xFFFFFFFF : v.mine() ? 0xFF8AE07A : C_TEXT);
			String line = v.war() ? Component.translatable("screen.airdefense.map.village_war", v.country()).getString()
					: v.mine() ? Component.translatable("screen.airdefense.map.village_mine", v.population(), v.soldiers()).getString()
					: v.country().isEmpty() ? Component.translatable("screen.airdefense.map.village_free", v.population()).getString()
					: v.country() + " · " + Component.translatable("screen.airdefense.map.village_guards", v.guards()).getString();
			small(g, line, x0 + 13, y + 11, C_DIM);
		}
		g.fill(x0 + 2, my1 - 92, x1 - 2, my1 - 91, 0xFF3A4652);
		NationMapPayload.Village sel = selectedVillage();
		Component l1;
		Component l2 = null;
		if (pickedVillage >= 0 && sel != null) {
			var cur = com.stasdoto.airdefense.nation.BuildingType.byId(pickedType);
			var to = REBUILD_TYPES[rebuildChoice];
			l1 = Component.translatable("screen.airdefense.map.picked", Component.translatable(cur.key()), sel.name());
			l2 = !sel.mine() ? Component.translatable("screen.airdefense.map.not_yours")
					: Component.translatable("screen.airdefense.map.rebuild_cost", to.wood, to.stone, to.iron);
		} else if (sel == null) {
			l1 = Component.translatable("screen.airdefense.map.select_village");
		} else if (!sel.mine()) {
			l1 = Component.literal(sel.name());
			l2 = Component.translatable(sel.war() ? "screen.airdefense.map.enemy" : "screen.airdefense.map.not_yours");
		} else {
			l1 = Component.translatable("screen.airdefense.map.army_of", sel.name(), sel.soldiers());
			l2 = target == null ? Component.translatable("screen.airdefense.map.pick_point")
					: Component.translatable("screen.airdefense.map.point", target.getX(), target.getZ());
		}
		small(g, l1.getString(), x0 + 5, my1 - 88, C_TEXT);
		if (l2 != null) {
			small(g, l2.getString(), x0 + 5, my1 - 79, sel != null && sel.war() ? 0xFFFF7A6A : C_DIM);
		}
	}

	/** Where the list ends: higher on the army tab (its buttons take more room). */
	private int listEnd() {
		return tab == 1 ? my1 - 94 : listBottom;
	}

	// ------------------------------------------------------------------------------------------------
	// Primitives

	private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	/** A dotted ({@code dash} > 0) or solid line of single pixels, clipped to the map. */
	private void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color, int dash) {
		double len = Math.hypot(x1 - x0, y1 - y0);
		int n = (int) Math.min(4000, Math.ceil(len));
		for (int i = 0; i <= n; i++) {
			if (dash > 0 && (i / dash) % 2 == 1) {
				continue;
			}
			double t = n == 0 ? 0 : (double) i / n;
			int x = (int) Math.floor(x0 + (x1 - x0) * t);
			int y = (int) Math.floor(y0 + (y1 - y0) * t);
			if (x >= mx0 && x < mx1 && y >= my0 && y < my1) {
				g.fill(x, y, x + 1, y + 1, color);
			}
		}
	}

	private void circle(GuiGraphicsExtractor g, double cx, double cy, double r, int color, int dash) {
		if (r < 1) {
			return;
		}
		int n = (int) Mth.clamp(r * Mth.TWO_PI / 1.5, 16, 900);
		for (int i = 0; i < n; i++) {
			if (dash > 0 && (i / dash) % 2 == 1) {
				continue;
			}
			double a = Mth.TWO_PI * i / n;
			int x = (int) Math.floor(cx + Math.cos(a) * r);
			int y = (int) Math.floor(cy + Math.sin(a) * r);
			if (x >= mx0 && x < mx1 && y >= my0 && y < my1) {
				g.fill(x, y, x + 1, y + 1, color);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// For the automated test

	/** For the automated test: villages on screen as {id, x, y}. */
	public List<int[]> villageScreenPositions() {
		List<int[]> out = new ArrayList<>();
		for (NationMapPayload.Village v : NationClient.villages()) {
			out.add(new int[]{v.id(), (int) toScreenX(v.x()), (int) toScreenY(v.z())});
		}
		return out;
	}

	public List<int[]> vehicleScreenPositions() {
		List<int[]> out = new ArrayList<>();
		for (MapStatusPayload.Entry e : entries()) {
			out.add(new int[]{e.id(), (int) toScreenX(e.x()), (int) toScreenY(e.z())});
		}
		return out;
	}
}
