package com.stasdoto.airdefense.client.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.stasdoto.airdefense.client.ui.UiButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.stasdoto.airdefense.item.DesignatorItem;
import com.stasdoto.airdefense.map.MapActionPayload;
import com.stasdoto.airdefense.map.MapStatusPayload;
import com.stasdoto.airdefense.map.RadarPayload;
import com.stasdoto.airdefense.missile.MissileType;
import com.stasdoto.airdefense.radar.RadarType;
import com.stasdoto.airdefense.registry.ModSounds;
import com.stasdoto.airdefense.vehicle.VehicleEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * The tablet's radar screen: a green scope centred on the player with range rings and a sweep, every contact the
 * radar stations see (what it is, how high, how fast, where it was launched and where it is heading), our air
 * defence and its reach. Works only while at least one radar station of ours is switched on and standing.
 */
public class RadarScreen extends Screen {
	private static final int PANEL_W = 176;
	private static final int[] RANGES = {100, 200, 400, 800, 1600};
	private static final int C_BG = 0xFF040D06;
	private static final int C_SCOPE = 0xFF071A0C;
	private static final int C_GRID = 0xFF1E5A2C;
	private static final int C_GREEN = 0xFF4CFF7A;
	private static final int C_DIMG = 0xFF2E9E4C;
	private static final int C_HOSTILE = 0xFFFF4A3A;
	private static final int C_DECOY = 0xFFC8C060;
	private static final int C_FRIEND = 0xFF7AD8FF;
	private static final int C_TARGET = 0xFFFFD24A;
	private static final int C_TEXT = 0xFFD8F0DC;

	@Nullable
	private static RadarPayload latest;
	private static final Map<Integer, Deque<float[]>> TRAILS = new HashMap<>();
	private static final Set<Integer> KNOWN = new HashSet<>();
	private static int pings;

	private int zoom = 2;
	private boolean zoomChosen;
	private int cx;
	private int cy;
	private int radius;
	private int px0;
	private int refresh;
	private double centerX;
	private double centerZ;
	/** Labels placed on the scope this frame (so they don't pile up on each other). */
	private final List<int[]> labels = new ArrayList<>();

	public RadarScreen() {
		super(Component.translatable("screen.airdefense.radar.title"));
	}

	/** A new answer from the server: keep the tracks, and ping for every new hostile contact. */
	public static void receive(RadarPayload p) {
		latest = p;
		Set<Integer> now = new HashSet<>();
		boolean fresh = false;
		for (RadarPayload.Contact c : p.contacts()) {
			now.add(c.id());
			Deque<float[]> trail = TRAILS.computeIfAbsent(c.id(), k -> new ArrayDeque<>());
			trail.addLast(new float[]{c.x(), c.z()});
			while (trail.size() > 14) {
				trail.removeFirst();
			}
			if (c.hostile() && KNOWN.add(c.id())) {
				fresh = true;
			}
		}
		TRAILS.keySet().retainAll(now);
		KNOWN.retainAll(now);
		Minecraft mc = Minecraft.getInstance();
		if (fresh && mc.gui.screen() instanceof RadarScreen) {
			pings++;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.RADAR_PING, 1.0f, 0.8f));
		}
	}

	/** For the automated test. */
	public static int contactCount() {
		RadarPayload p = latest;
		return p == null ? 0 : p.contacts().size();
	}

	public static int stationCount() {
		RadarPayload p = latest;
		return p == null ? 0 : p.stations().size();
	}

	@Override
	protected void init() {
		px0 = width - PANEL_W - 4;
		cx = (px0 - 4) / 2 + 2;
		cy = height / 2 + 6;
		radius = Math.max(40, Math.min((px0 - 8) / 2, height / 2 - 18));
		addRenderableWidget(UiButton.builder(Component.translatable("screen.airdefense.radar.to_map"), b -> minecraft.gui.setScreen(new TacticalMapScreen()))
				.bounds(px0 + 4, height - 24, PANEL_W - 8, 20).build());
		addRenderableWidget(UiButton.builder(Component.literal("+"), b -> zoomBy(-1)).bounds(6, height - 24, 20, 20).build());
		addRenderableWidget(UiButton.builder(Component.literal("-"), b -> zoomBy(1)).bounds(28, height - 24, 20, 20).build());
		Player p = minecraft.player;
		if (p != null) {
			centerX = p.getX();
			centerZ = p.getZ();
		}
		MapClient.send(MapActionPayload.radar());
	}

	private void zoomBy(int d) {
		zoom = Mth.clamp(zoom + d, 0, RANGES.length - 1);
		zoomChosen = true;
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
		centerX = p.getX();
		centerZ = p.getZ();
		if (++refresh % 10 == 0) {
			MapClient.send(MapActionPayload.radar());
		}
		if (!zoomChosen && latest != null && !latest.stations().isEmpty()) {
			// Fit the farthest station's cover.
			double need = 0;
			for (RadarPayload.Station s : latest.stations()) {
				RadarType r = VehicleType.byId(s.vehicleType()).radar;
				double range = r != null ? r.range : 200;
				need = Math.max(need, Math.hypot(s.x() - centerX, s.z() - centerZ) + range);
			}
			int z = 0;
			while (z < RANGES.length - 1 && RANGES[z] < need * 0.9) {
				z++;
			}
			zoom = z;
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x < px0) {
			zoomBy(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private double range() {
		return RANGES[zoom];
	}

	private double sx(double worldX) {
		return cx + (worldX - centerX) / range() * radius;
	}

	private double sy(double worldZ) {
		return cy + (worldZ - centerZ) / range() * radius;
	}

	private boolean inScope(double x, double y) {
		return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= (double) radius * radius;
	}

	// ------------------------------------------------------------------------------------------------
	// Drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, C_BG);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long t0 = System.nanoTime();
		draw(g, mouseX, mouseY, partialTick);
		frameNanos = System.nanoTime() - t0;
	}

	/** For the tests: how long the last frame's drawing took (ns). */
	public static long frameNanos;

	private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		RadarPayload p = latest;
		boolean working = p != null && !p.stations().isEmpty();
		labels.clear();
		drawScope(g, working);
		if (working) {
			drawStations(g, p);
			drawDefense(g);
			drawSweep(g);
			drawContacts(g, p);
		} else {
			Component a = Component.translatable("screen.airdefense.radar.no_signal");
			Component b = Component.translatable("screen.airdefense.radar.no_signal_hint");
			g.text(font, a, cx - font.width(a) / 2, cy - 12, C_HOSTILE);
			g.textWithWordWrap(font, b, cx - radius + 20, cy + 2, radius * 2 - 40, C_DIMG);
		}
		drawPlayer(g);
		g.text(font, title, 6, 5, C_GREEN);
		String scale = Component.translatable("screen.airdefense.radar.scale", (int) range()).getString();
		g.text(font, scale, 54, height - 18, C_DIMG);
		drawPanel(g, p, working);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void drawScope(GuiGraphicsExtractor g, boolean working) {
		for (int dy = -radius; dy <= radius; dy++) {
			int w = (int) Math.sqrt((double) radius * radius - (double) dy * dy);
			g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, C_SCOPE);
		}
		int rings = 4;
		for (int i = 1; i <= rings; i++) {
			circle(g, cx, cy, radius * i / (double) rings, i == rings ? C_DIMG : C_GRID, i == rings ? 0 : 2);
			String label = (int) (range() * i / rings) + "";
			small(g, label, cx + 3, cy - (int) (radius * i / (double) rings) + 2, C_DIMG);
		}
		line(g, cx - radius, cy, cx + radius, cy, C_GRID, 3);
		line(g, cx, cy - radius, cx, cy + radius, C_GRID, 3);
		// Bearings every 30 degrees on the rim; north is up (-Z).
		for (int a = 0; a < 360; a += 30) {
			double rad = Math.toRadians(a);
			double ex = cx + Math.sin(rad) * radius;
			double ey = cy - Math.cos(rad) * radius;
			line(g, cx + Math.sin(rad) * (radius - 5), cy - Math.cos(rad) * (radius - 5), ex, ey, C_DIMG, 0);
			String t = a == 0 ? "N" : a == 90 ? "E" : a == 180 ? "S" : a == 270 ? "W" : String.valueOf(a);
			small(g, t, (int) (cx + Math.sin(rad) * (radius + 8)) - 3, (int) (cy - Math.cos(rad) * (radius + 8)) - 3, working ? C_GREEN : C_DIMG);
		}
	}

	private double sweepAngle() {
		return (System.currentTimeMillis() % 4000L) / 4000.0 * Mth.TWO_PI;
	}

	private void drawSweep(GuiGraphicsExtractor g) {
		double a = sweepAngle();
		for (int i = 0; i < 18; i++) {
			double b = a - i * 0.035;
			int alpha = Math.max(0, 0xA0 - i * 9);
			int color = (alpha << 24) | 0x4CFF7A;
			line(g, cx, cy, cx + Math.sin(b) * radius, cy - Math.cos(b) * radius, color, 0);
		}
	}

	private void drawStations(GuiGraphicsExtractor g, RadarPayload p) {
		for (RadarPayload.Station s : p.stations()) {
			VehicleType type = VehicleType.byId(s.vehicleType());
			RadarType r = type.radar;
			double x = sx(s.x());
			double y = sy(s.z());
			if (r != null) {
				double rr = r.range / range() * radius;
				if (r.rotates()) {
					circle(g, x, y, rr, 0x704CFF7A, 4);
				} else {
					// Sector radar: its arc and edges.
					double look = Math.toRadians(s.yaw() + r.facing);
					double half = Math.toRadians(r.sector / 2);
					for (int k = 0; k <= 60; k++) {
						double b = look - half + 2 * half * k / 60.0;
						dot(g, x - Math.sin(b) * rr, y + Math.cos(b) * rr, 0x904CFF7A);
					}
					for (double b : new double[]{look - half, look + half}) {
						line(g, x, y, x - Math.sin(b) * rr, y + Math.cos(b) * rr, 0x704CFF7A, 3);
					}
				}
			}
			if (inScope(x, y)) {
				g.fill((int) x - 3, (int) y - 3, (int) x + 4, (int) y + 4, 0xFF0C2A14);
				g.fill((int) x - 2, (int) y - 2, (int) x + 3, (int) y + 3, C_GREEN);
				small(g, Component.translatable("map.airdefense.short." + type.id).getString(), (int) x + 5, (int) y - 3, C_GREEN);
			}
		}
	}

	private void drawDefense(GuiGraphicsExtractor g) {
		for (MapStatusPayload.Entry e : MapClient.vehicles()) {
			VehicleType type = VehicleType.byId(e.type());
			if (type.defense == null) {
				continue;
			}
			double x = sx(e.x());
			double y = sy(e.z());
			boolean on = e.mode() != VehicleEntity.MODE_OFF;
			circle(g, x, y, type.defense.range / range() * radius, on ? 0x604FB8E8 : 0x30808080, 2);
			if (inScope(x, y)) {
				int c = on ? C_FRIEND : 0xFF808890;
				g.fill((int) x - 1, (int) y - 3, (int) x + 2, (int) y - 2, c);
				g.fill((int) x - 2, (int) y - 2, (int) x + 3, (int) y, c);
				g.fill((int) x - 3, (int) y, (int) x + 4, (int) y + 2, c);
				small(g, Component.translatable("map.airdefense.short." + type.id).getString(), (int) x + 5, (int) y - 3, c);
			}
		}
	}

	private void drawContacts(GuiGraphicsExtractor g, RadarPayload p) {
		double sweep = sweepAngle();
		for (RadarPayload.Contact c : p.contacts()) {
			double x = sx(c.x());
			double y = sy(c.z());
			int color = !c.hostile() ? C_FRIEND : c.decoy() ? C_DECOY : C_HOSTILE;
			// Track: where it has been.
			Deque<float[]> trail = TRAILS.get(c.id());
			if (trail != null) {
				float[] prev = null;
				for (float[] q : trail) {
					if (prev != null) {
						line(g, sx(prev[0]), sy(prev[1]), sx(q[0]), sy(q[1]), (color & 0xFFFFFF) | 0x60000000, 0);
					}
					prev = q;
				}
			}
			if (!c.hostile()) {
				if (inScope(x, y)) {
					g.fill((int) x - 1, (int) y - 1, (int) x + 1, (int) y + 1, color);
				}
				continue;
			}
			// Launch point.
			double lx = sx(c.lx());
			double ly = sy(c.lz());
			if (inScope(lx, ly)) {
				line(g, lx - 3, ly - 3, lx + 3, ly + 3, C_HOSTILE, 0);
				line(g, lx - 3, ly + 3, lx + 3, ly - 3, C_HOSTILE, 0);
				label(g, Component.translatable("screen.airdefense.radar.launch").getString(), (int) lx + 5, (int) ly + 2, C_HOSTILE);
			}
			// Where it is heading.
			if (!c.decoy()) {
				double tx = sx(c.tx());
				double ty = sy(c.tz());
				line(g, x, y, tx, ty, 0x80FFD24A, 3);
				if (inScope(tx, ty)) {
					circle(g, tx, ty, 4, C_TARGET, 0);
					label(g, Component.translatable("screen.airdefense.radar.target").getString(), (int) tx + 6, (int) ty - 3, C_TARGET);
				}
			}
			if (!inScope(x, y)) {
				continue;
			}
			// The blip glows up as the sweep passes over it.
			double bearing = Math.atan2(c.x() - centerX, -(c.z() - centerZ));
			double since = (sweep - bearing) % Mth.TWO_PI;
			if (since < 0) {
				since += Mth.TWO_PI;
			}
			int size = since < 0.6 ? 3 : 2;
			g.fill((int) x - size, (int) y - size, (int) x + size + 1, (int) y + size + 1, color);
			if (c.engaged() > 0) {
				circle(g, x, y, 6, C_FRIEND, 0);
			}
			String text = name(c) + " " + Math.round(c.height()) + "m";
			label(g, text, (int) x + 6, (int) y - 9, color);
		}
	}

	private void drawPlayer(GuiGraphicsExtractor g) {
		g.fill(cx - 1, cy - 4, cx + 2, cy + 5, 0xFFFFFFFF);
		g.fill(cx - 4, cy - 1, cx + 5, cy + 2, 0xFFFFFFFF);
	}

	private void drawPanel(GuiGraphicsExtractor g, @Nullable RadarPayload p, boolean working) {
		int x0 = px0;
		int x1 = px0 + PANEL_W;
		g.fill(x0, 4, x1, height - 28, 0xF0061409);
		g.fill(x0, 4, x1, 5, C_DIMG);
		int y = 9;
		int stations = p == null ? 0 : p.stations().size();
		g.text(font, Component.translatable("screen.airdefense.radar.stations", stations), x0 + 5, y, working ? C_GREEN : C_HOSTILE);
		y += 11;
		if (!working) {
			return;
		}
		List<RadarPayload.Contact> hostile = new ArrayList<>();
		int drones = 0;
		int cruise = 0;
		int ballistic = 0;
		int ours = 0;
		for (RadarPayload.Contact c : p.contacts()) {
			if (!c.hostile()) {
				ours++;
				continue;
			}
			hostile.add(c);
			switch (MissileType.byId(c.type()).kind) {
				case DRONE -> drones++;
				case CRUISE -> cruise++;
				default -> ballistic++;
			}
		}
		g.text(font, Component.translatable("screen.airdefense.radar.threats", hostile.size()), x0 + 5, y, hostile.isEmpty() ? C_GREEN : C_HOSTILE);
		y += 10;
		small(g, Component.translatable("screen.airdefense.radar.kinds", drones, cruise, ballistic, ours).getString(), x0 + 5, y, C_DIMG);
		y += 10;
		g.fill(x0 + 2, y, x1 - 2, y + 1, C_GRID);
		y += 4;
		hostile.sort(Comparator.comparingDouble(RadarScreen::eta));
		int rows = Math.max(0, (height - 34 - y) / 20);
		for (int i = 0; i < Math.min(rows, hostile.size()); i++) {
			RadarPayload.Contact c = hostile.get(i);
			int color = c.decoy() ? C_DECOY : C_HOSTILE;
			g.text(font, name(c), x0 + 5, y, color);
			String h = Math.round(c.height()) + "m";
			small(g, h, x1 - 6 - (int) (font.width(h) * 0.75f), y + 1, C_TEXT);
			double eta = eta(c);
			String line2 = Component.translatable(c.engaged() > 0 ? "screen.airdefense.radar.row_engaged" : "screen.airdefense.radar.row",
					kmh(c), eta < 999 ? String.format(Locale.ROOT, "%.0f", eta) : "-").getString();
			if (!c.decoy()) {
				line2 = line2 + " · " + com.stasdoto.airdefense.map.Grid.square(c.tx(), c.tz(), MapClient.cyrillic());
			}
			small(g, line2, x0 + 5, y + 10, c.engaged() > 0 ? C_FRIEND : C_DIMG);
			y += 20;
		}
		if (hostile.size() > rows) {
			small(g, "+" + (hostile.size() - rows), x0 + 5, y, C_DIMG);
		}
	}

	private static String name(RadarPayload.Contact c) {
		if (c.decoy()) {
			return Component.translatable("radar.airdefense.decoy").getString();
		}
		MissileType t = MissileType.byId(c.type());
		return Component.translatable("radar.airdefense.contact." + t.name().toLowerCase(Locale.ROOT)).getString();
	}

	private static int kmh(RadarPayload.Contact c) {
		return (int) Math.round(Math.hypot(c.vx(), c.vz()) * 20 * 3.6);
	}

	/** Seconds until it reaches its target (on the ground), at its current ground speed. */
	private static double eta(RadarPayload.Contact c) {
		double v = Math.hypot(c.vx(), c.vz());
		double d = Math.hypot(c.tx() - c.x(), c.tz() - c.z());
		if (!c.hostile() || c.decoy() || v < 0.01) {
			return 9999;
		}
		return d / v / 20.0;
	}

	// ------------------------------------------------------------------------------------------------
	// Primitives (clipped to the scope)

	private void dot(GuiGraphicsExtractor g, double x, double y, int color) {
		int ix = (int) Math.floor(x);
		int iy = (int) Math.floor(y);
		if (inScope(ix, iy)) {
			g.fill(ix, iy, ix + 1, iy + 1, color);
		}
	}

	private void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color, int dash) {
		GuiDraw.line(g, GuiDraw.Clip.disc(cx, cy, radius), x0, y0, x1, y1, 1f, color, dash);
	}

	private void circle(GuiGraphicsExtractor g, double x, double y, double r, int color, int dash) {
		GuiDraw.circle(g, GuiDraw.Clip.disc(cx, cy, radius), x, y, r, 1f, color, dash);
	}

	/** A small label where it does not cover another one (tried a little above and below; left out if no room). */
	private void label(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		int w = (int) (font.width(text) * 0.75f) + 1;
		for (int dy : new int[]{0, -8, 8, -16, 16}) {
			int yy = y + dy;
			boolean free = true;
			for (int[] r : labels) {
				if (x < r[0] + r[2] && x + w > r[0] && yy < r[1] + 7 && yy + 7 > r[1]) {
					free = false;
					break;
				}
			}
			if (free) {
				labels.add(new int[]{x, yy, w});
				small(g, text, x, yy, color);
				return;
			}
		}
	}

	private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font, text, 0, 0, color);
		g.pose().popMatrix();
	}
}
