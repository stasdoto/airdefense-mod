package com.stasdoto.airdefense.client.nation;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.stasdoto.airdefense.nation.BuildingType;
import com.stasdoto.airdefense.nation.Economy;
import com.stasdoto.airdefense.nation.NationActionPayload;
import com.stasdoto.airdefense.nation.SettlementInfoPayload;
import com.stasdoto.airdefense.nation.VillageEconomyPayload;
import com.stasdoto.airdefense.nation.WorkerEntity;
import com.stasdoto.airdefense.vehicle.VehicleType;

/**
 * A village's affairs (Shift + right click on one of its people, or from the tablet map). For any village: who owns
 * it, how many live there, how much it respects you, the charter that makes it yours (in creative, taking it at once).
 * For your own villages there are tabs: the overview with the army, the people at work and the store, the buildings
 * to order, and the hangar's vehicles.
 */
public class SettlementScreen extends Screen {
	public static final int OVERVIEW = 0;
	public static final int WORK = 1;
	public static final int BUILD = 2;
	public static final int HANGAR = 3;

	private static final int C_TEXT = 0xFFE6E9EC;
	private static final int C_DIM = 0xFF9AA4AE;
	private static final int C_OK = 0xFF8AE07A;
	private static final int C_BAD = 0xFFFF7A6A;
	private static final int C_GOLD = 0xFFFFD24A;
	private static final int ROW = 15;

	private static final ItemStack[] RESOURCE_ICONS = {new ItemStack(Items.OAK_LOG), new ItemStack(Items.COBBLESTONE), new ItemStack(Items.IRON_INGOT)};
	private static final ItemStack[] JOB_ICONS = {new ItemStack(Items.IRON_AXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE),
			new ItemStack(Items.IRON_SHOVEL)};
	private static final String[] JOB_KEYS = {"wood", "stone", "iron", "build"};
	private static final VehicleType[] HANGAR_ORDER = {VehicleType.GEPARD, VehicleType.NASAMS, VehicleType.IRIS_T, VehicleType.PATRIOT,
			VehicleType.HIMARS, VehicleType.SHAHED, VehicleType.ISKANDER, VehicleType.KALIBR};

	private SettlementInfoPayload info;
	@Nullable
	private VillageEconomyPayload eco;
	private int tab = OVERVIEW;
	private int x0;
	private int y0;
	private int w;
	private int h;
	private int refresh;

	private Button buy;
	private Button take;
	private Button callOne;
	private Button callAll;
	private Button recall;
	private Button dismiss;
	private final Button[] tabs = new Button[4];
	private final List<Button> workButtons = new ArrayList<>();
	private final List<Button> buildButtons = new ArrayList<>();
	private final List<Button> hangarButtons = new ArrayList<>();
	private Button cancel;
	private Button calm;

	public SettlementScreen(SettlementInfoPayload info) {
		this(info, null);
	}

	public SettlementScreen(SettlementInfoPayload info, @Nullable VillageEconomyPayload eco) {
		super(Component.literal(info.name()));
		this.info = info;
		this.eco = eco;
	}

	public int id() {
		return info.id();
	}

	public SettlementInfoPayload info() {
		return info;
	}

	@Nullable
	public VillageEconomyPayload economy() {
		return eco;
	}

	public void update(SettlementInfoPayload p) {
		info = p;
		if (!info.mine()) {
			tab = OVERVIEW;
		}
		updateButtons();
	}

	public void update(VillageEconomyPayload p) {
		eco = p;
		updateButtons();
	}

	/** For the automated test (and the tab buttons). */
	public void setTab(int t) {
		tab = info.mine() ? t : OVERVIEW;
		updateButtons();
	}

	public int tab() {
		return tab;
	}

	@Override
	protected void init() {
		w = Math.min(width - 12, 340);
		h = Math.min(height - 12, 236);
		x0 = (width - w) / 2;
		y0 = (height - h) / 2;
		workButtons.clear();
		buildButtons.clear();
		hangarButtons.clear();
		int bw = (w - 18) / 2;
		int by = y0 + h - 48;
		buy = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.buy", info.price()),
				b -> send(NationActionPayload.BUY, 0, 0)).bounds(x0 + 6, by + 24, bw, 20).build());
		take = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.take"),
				b -> send(NationActionPayload.TAKE, 0, 0)).bounds(x0 + 12 + bw, by + 24, bw, 20).build());
		callOne = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.call_one"),
				b -> send(NationActionPayload.MOBILIZE, 1, 0)).bounds(x0 + 6, by, bw, 20).build());
		callAll = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.call_all"),
				b -> send(NationActionPayload.MOBILIZE, 99, 0)).bounds(x0 + 12 + bw, by, bw, 20).build());
		recall = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.recall"),
				b -> send(NationActionPayload.RECALL, 0, 0)).bounds(x0 + 6, by + 24, bw, 20).build());
		dismiss = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.dismiss"),
				b -> send(NationActionPayload.DEMOBILIZE, 0, 0)).bounds(x0 + 12 + bw, by + 24, bw, 20).build());
		calm = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.calm", 0),
				b -> send(NationActionPayload.CALM, 0, 0)).bounds(x0 + w - 136, y0 + 54, 128, 14).build());
		// Tabs.
		int tw = (w - 16 - 9) / 4;
		String[] names = {"overview", "work", "build", "hangar"};
		for (int i = 0; i < 4; i++) {
			int t = i;
			tabs[i] = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.tab." + names[i]), b -> setTab(t))
					.bounds(x0 + 8 + i * (tw + 3), y0 + 32, tw, 16).build());
		}
		int top = contentTop();
		// Work: minus and plus for every job; hand over resources; everybody home.
		for (int j = 0; j < WorkerEntity.JOBS; j++) {
			int job = j;
			int y = top + j * 20;
			workButtons.add(addRenderableWidget(Button.builder(Component.literal("-"), b -> send(NationActionPayload.JOB, job, -1))
					.bounds(x0 + 150, y, 18, 16).build()));
			workButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> send(NationActionPayload.JOB, job, 1))
					.bounds(x0 + 171, y, 18, 16).build()));
		}
		workButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.donate"),
				b -> send(NationActionPayload.DONATE, 0, 0)).bounds(x0 + 6, y0 + h - 24, bw, 18).build()));
		workButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.workers_home"),
				b -> send(NationActionPayload.WORKERS_HOME, 0, 0)).bounds(x0 + 12 + bw, y0 + h - 24, bw, 18).build()));
		// Building: a button per kind of building, cancel the last one ordered.
		int rows = top + 24;
		for (BuildingType t : BuildingType.values()) {
			buildButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.build_it"),
					b -> send(NationActionPayload.BUILD, t.ordinal(), 0)).bounds(x0 + w - 74, rows + t.ordinal() * ROW, 66, 14).build()));
		}
		cancel = addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.cancel"), b -> {
			if (eco != null && !eco.queue().isEmpty()) {
				send(NationActionPayload.CANCEL, eco.queue().size() - 1, 0);
			}
		}).bounds(x0 + w - 74, top, 66, 14).build());
		buildButtons.add(cancel);
		// Hangar: a button per vehicle.
		for (int i = 0; i < HANGAR_ORDER.length; i++) {
			VehicleType v = HANGAR_ORDER[i];
			hangarButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.airdefense.village.order_it"),
					b -> send(NationActionPayload.VEHICLE, v.ordinal(), 0)).bounds(x0 + w - 74, rows + i * ROW, 66, 14).build()));
		}
		updateButtons();
	}

	private int contentTop() {
		return y0 + (info.mine() ? 54 : 34);
	}

	/** For the automated test: what a button press does. */
	public void act(int action, int a) {
		send(action, a, 0);
	}

	public void act(int action, int a, int x) {
		send(action, a, x);
	}

	private void send(int action, int a, int x) {
		NationClient.send(action, info.id(), a, x, 0, 0);
	}

	private void updateButtons() {
		if (buy == null) {
			return;
		}
		boolean mine = info.mine();
		boolean overview = tab == OVERVIEW;
		buy.visible = !mine;
		buy.active = info.problem().isEmpty();
		buy.setMessage(Component.translatable("screen.airdefense.village.buy", info.price()));
		take.visible = !mine && info.creative();
		callOne.visible = mine && overview;
		callAll.visible = mine && overview;
		recall.visible = mine && overview;
		dismiss.visible = mine && overview;
		callOne.active = info.mobilizable() > 0;
		callAll.active = info.mobilizable() > 0;
		callAll.setMessage(Component.translatable("screen.airdefense.village.call_all", info.mobilizable()));
		recall.active = info.soldiers() > 0;
		dismiss.active = info.soldiers() > 0;
		for (int i = 0; i < tabs.length; i++) {
			tabs[i].visible = mine;
			tabs[i].active = tab != i;
		}
		calm.visible = mine && overview && eco != null && (eco.mood() < 0 || eco.rebels() > 0);
		if (eco != null) {
			calm.setMessage(Component.translatable("screen.airdefense.village.calm", eco.calmPrice()));
		}
		for (Button b : workButtons) {
			b.visible = mine && tab == WORK;
		}
		for (Button b : buildButtons) {
			b.visible = mine && tab == BUILD;
		}
		for (Button b : hangarButtons) {
			b.visible = mine && tab == HANGAR && eco != null && eco.hangar();
		}
		if (eco != null) {
			for (int j = 0; j < WorkerEntity.JOBS; j++) {
				workButtons.get(j * 2).active = j < eco.jobs().size() && eco.jobs().get(j) > 0;
				workButtons.get(j * 2 + 1).active = eco.idle() > 0;
			}
			for (BuildingType t : BuildingType.values()) {
				buildButtons.get(t.ordinal()).active = eco.queue().size() < Economy.MAX_QUEUE
						&& (eco.free() || t == BuildingType.ROADS || affordable(t.wood, t.stone, t.iron));
			}
			cancel.active = !eco.queue().isEmpty() && eco.queue().getLast() % 1000 == 0;
			for (int i = 0; i < HANGAR_ORDER.length; i++) {
				int[] c = Economy.vehicleCost(HANGAR_ORDER[i]);
				hangarButtons.get(i).active = eco.hangarQueue().size() < Economy.MAX_HANGAR_QUEUE && (eco.free() || affordable(c[0], c[1], c[2]));
			}
		}
	}

	private boolean affordable(int wood, int stone, int iron) {
		return eco != null && eco.wood() >= wood && eco.stone() >= stone && eco.iron() >= iron;
	}

	@Override
	public void tick() {
		if (++refresh % 20 == 0) {
			send(NationActionPayload.INFO, 0, 0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xA0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF4A5560);
		g.fill(x0, y0, x0 + w, y0 + h, 0xF0182028);
		int x = x0 + 8;
		int y = y0 + 7;
		g.text(font, Component.literal(info.name()), x, y, C_GOLD);
		y += 13;
		if (info.country().isEmpty()) {
			g.text(font, Component.translatable("screen.airdefense.village.independent"), x, y, C_DIM);
		} else {
			g.fill(x, y, x + 9, y + 8, info.color());
			Component c = Component.literal(info.country());
			if (info.cityState()) {
				c = Component.translatable("screen.airdefense.village.city_state", info.country());
			}
			g.text(font, c, x + 13, y, info.mine() ? C_OK : C_TEXT);
		}
		if (info.mine()) {
			int tw = (w - 16 - 9) / 4;
			g.fill(x0 + 8 + tab * (tw + 3), y0 + 49, x0 + 8 + tab * (tw + 3) + tw, y0 + 51, C_GOLD);
		}
		switch (info.mine() ? tab : OVERVIEW) {
			case WORK -> work(g);
			case BUILD -> build(g);
			case HANGAR -> hangar(g);
			default -> overview(g);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	// ------------------------------------------------------------------------------------------------
	// Tabs

	private void overview(GuiGraphicsExtractor g) {
		int x = x0 + 8;
		int y = contentTop();
		if (!info.elder().isEmpty()) {
			g.text(font, Component.translatable("screen.airdefense.village.elder", info.elder()), x, y, C_TEXT);
			y += 11;
		}
		g.text(font, Component.translatable("screen.airdefense.village.people", info.population(), info.guards()), x, y, C_TEXT);
		y += 11;
		if (info.mine()) {
			g.text(font, Component.translatable("screen.airdefense.village.army", info.soldiers(), info.mobilizable()), x, y, C_TEXT);
			y += 11;
			if (eco != null) {
				int workers = 0;
				for (int n : eco.jobs()) {
					workers += n;
				}
				g.text(font, Component.translatable("screen.airdefense.village.summary", workers, eco.beds(), eco.freeBeds()), x, y, C_TEXT);
				y += 11;
				int buildings = 0;
				for (int n : eco.built()) {
					buildings += n;
				}
				g.text(font, Component.translatable("screen.airdefense.village.summary2", buildings, eco.births()), x, y, C_DIM);
				y += 11;
				if (eco.birthEvery() > 0) {
					int min = Math.max(1, Math.round(eco.birthEvery() / 60f));
					g.text(font, Component.translatable("screen.airdefense.village.births", min), x, y, C_OK);
				} else {
					g.text(font, Component.translatable("screen.airdefense.village.no_births"), x, y, C_BAD);
				}
				y += 11;
				// The mood, and what makes it.
				int mood = eco.mood();
				String word = mood >= 10 ? "happy" : mood >= 0 ? "calm" : mood >= -15 ? "unhappy" : "angry";
				int moodColor = mood >= 10 ? C_OK : mood >= 0 ? C_TEXT : mood >= -15 ? 0xFFFFB04A : C_BAD;
				g.text(font, Component.translatable("screen.airdefense.village.mood",
						Component.translatable("screen.airdefense.village.mood." + word), (mood > 0 ? "+" : "") + mood), x, y, moodColor);
				y += 10;
				StringBuilder why = new StringBuilder();
				for (int code : eco.moodReasons()) {
					int reason = code / 1000;
					int value = code % 1000 - 500;
					if (reason == 0 || value == 0) {
						continue;
					}
					if (why.length() > 0) {
						why.append(", ");
					}
					why.append(Component.translatable("screen.airdefense.village.mood.reason." + reason).getString()).append(' ')
							.append(value > 0 ? "+" : "").append(value);
				}
				if (why.length() > 0) {
					small(g, why.toString(), x, y, C_DIM);
					y += 9 * Math.max(1, (int) Math.ceil(font.width(why.toString()) * 0.75 / (w - 16)));
				}
				if (eco.rebels() > 0) {
					g.text(font, Component.translatable("screen.airdefense.village.riot", eco.rebels()), x, y + 1, C_BAD);
					y += 12;
				}
			}
		}
		// Respect: a bar from -100 to +100 with the threshold for the charter.
		int rep = info.reputation();
		g.text(font, Component.translatable("screen.airdefense.village.respect", rep), x, y, rep >= 25 ? C_OK : rep < 0 ? C_BAD : C_TEXT);
		y += 11;
		int bw = w - 16;
		g.fill(x, y, x + bw, y + 4, 0xFF303A44);
		int mid = x + bw / 2;
		int pos = mid + Math.max(-bw / 2, Math.min(bw / 2, rep * bw / 200));
		g.fill(Math.min(mid, pos), y, Math.max(mid, pos), y + 4, rep >= 0 ? C_OK : C_BAD);
		int need = mid + 25 * bw / 200;
		g.fill(need, y - 2, need + 1, y + 6, C_GOLD);
		y += 10;
		if (!info.mine()) {
			if (!info.problem().isEmpty()) {
				g.textWithWordWrap(font, Component.literal(info.problem()), x, y, w - 16, C_BAD);
			} else {
				g.textWithWordWrap(font, Component.translatable("screen.airdefense.village.can_buy"), x, y, w - 16, C_OK);
			}
			y += 20;
			small(g, Component.translatable("screen.airdefense.village.how").getString(), x, Math.min(y, y0 + h - 64), C_DIM);
		}
	}

	private void work(GuiGraphicsExtractor g) {
		int top = contentTop();
		if (eco == null) {
			g.text(font, Component.translatable("screen.airdefense.village.loading"), x0 + 8, top, C_DIM);
			return;
		}
		for (int j = 0; j < WorkerEntity.JOBS; j++) {
			int y = top + j * 20;
			g.item(JOB_ICONS[j], x0 + 8, y);
			g.text(font, Component.translatable("screen.airdefense.village.job." + JOB_KEYS[j]), x0 + 28, y + 4, C_TEXT);
			String n = String.valueOf(j < eco.jobs().size() ? eco.jobs().get(j) : 0);
			g.text(font, n, x0 + 144 - font.width(n), y + 4, C_GOLD);
		}
		int y = top + 82;
		g.text(font, Component.translatable("screen.airdefense.village.idle", eco.idle()), x0 + 8, y, eco.idle() > 0 ? C_OK : C_DIM);
		small(g, Component.translatable("screen.airdefense.village.work_hint").getString(), x0 + 8, y + 12, C_DIM);
		// The store, on the right.
		int sx = x0 + 200;
		int sw = x0 + w - 8 - sx;
		g.text(font, Component.translatable("screen.airdefense.village.store"), sx, top, C_GOLD);
		for (int k = 0; k < 3; k++) {
			int ry = top + 14 + k * 22;
			g.item(RESOURCE_ICONS[k], sx, ry);
			String amount = eco.free() ? "∞" : eco.stock(k) + " / " + eco.cap();
			g.text(font, Component.translatable("screen.airdefense.village.res." + k), sx + 20, ry, C_TEXT);
			g.text(font, amount, sx + 20, ry + 9, eco.free() ? C_OK : C_DIM);
			int bx = sx + 20;
			int bw = sw - 20;
			g.fill(bx, ry + 18, bx + bw, ry + 20, 0xFF303A44);
			int fillW = eco.free() ? bw : (int) ((long) bw * Math.min(eco.stock(k), eco.cap()) / Math.max(1, eco.cap()));
			g.fill(bx, ry + 18, bx + fillW, ry + 20, k == 0 ? 0xFFB08850 : k == 1 ? 0xFFA0A0A0 : 0xFFD8D8E0);
		}
	}

	private void build(GuiGraphicsExtractor g) {
		int top = contentTop();
		if (eco == null) {
			g.text(font, Component.translatable("screen.airdefense.village.loading"), x0 + 8, top, C_DIM);
			return;
		}
		int x = x0 + 8;
		if (eco.queue().isEmpty()) {
			g.text(font, Component.translatable("screen.airdefense.village.nothing_building"), x, top + 3, C_DIM);
		} else {
			int first = eco.queue().getFirst();
			BuildingType t = BuildingType.byId(first / 1000);
			g.text(font, Component.translatable("screen.airdefense.village.building_now", Component.translatable(t.key()), first % 1000,
					eco.builders()), x, top + 3, C_OK);
			int more = eco.queue().size() - 1;
			if (!eco.free() && eco.jobs().size() > WorkerEntity.BUILD && eco.jobs().get(WorkerEntity.BUILD) == 0) {
				g.text(font, Component.translatable("screen.airdefense.village.no_builders"), x, top + 13, C_BAD);
			} else if (more > 0) {
				g.text(font, Component.translatable("screen.airdefense.village.queued", more), x, top + 13, C_DIM);
			}
		}
		int rows = top + 24;
		for (BuildingType t : BuildingType.values()) {
			int y = rows + t.ordinal() * ROW;
			int n = eco.builtCount(t);
			Component name = Component.translatable(t.key());
			if (n > 0) {
				name = Component.translatable("screen.airdefense.village.built_count", name, n);
			}
			g.text(font, name, x, y + 3, C_TEXT);
			if (t == BuildingType.ROADS && !eco.free()) {
				small(g, Component.translatable("screen.airdefense.village.roads_cost").getString(), x0 + 128, y + 4, C_DIM);
			} else {
				cost(g, x0 + 128, y + 1, t.wood, t.stone, t.iron);
			}
		}
	}

	private void hangar(GuiGraphicsExtractor g) {
		int top = contentTop();
		int x = x0 + 8;
		if (eco == null) {
			g.text(font, Component.translatable("screen.airdefense.village.loading"), x, top, C_DIM);
			return;
		}
		if (!eco.hangar()) {
			g.textWithWordWrap(font, Component.translatable("screen.airdefense.village.need_hangar"), x, top + 4, w - 16, C_DIM);
			return;
		}
		if (eco.hangarQueue().isEmpty()) {
			g.text(font, Component.translatable("screen.airdefense.village.hangar_idle"), x, top + 3, C_DIM);
		} else {
			VehicleType v = VehicleType.byId(eco.hangarQueue().getFirst());
			g.text(font, Component.translatable("screen.airdefense.village.making", Component.translatable("entity.airdefense." + v.id),
					eco.hangarPercent()), x, top + 3, C_OK);
			if (eco.hangarQueue().size() > 1) {
				g.text(font, Component.translatable("screen.airdefense.village.queued", eco.hangarQueue().size() - 1), x, top + 13, C_DIM);
			}
		}
		int rows = top + 24;
		for (int i = 0; i < HANGAR_ORDER.length; i++) {
			VehicleType v = HANGAR_ORDER[i];
			int y = rows + i * ROW;
			g.text(font, Component.translatable("entity.airdefense." + v.id), x, y + 3, C_TEXT);
			int[] c = Economy.vehicleCost(v);
			cost(g, x0 + 128, y + 1, c[0], c[1], c[2]);
			String time = Component.translatable("screen.airdefense.village.seconds", eco.free() ? Math.max(1, c[3] / 10) : c[3]).getString();
			g.text(font, time, x0 + w - 80 - font.width(time), y + 3, C_DIM);
		}
	}

	/** Wood, stone and iron with little icons; red where the store has too little, "free" in creative. */
	private void cost(GuiGraphicsExtractor g, int x, int y, int wood, int stone, int iron) {
		if (eco != null && eco.free()) {
			g.text(font, Component.translatable("screen.airdefense.village.free"), x, y + 2, C_OK);
			return;
		}
		int[] need = {wood, stone, iron};
		int cx = x;
		for (int k = 0; k < 3; k++) {
			if (need[k] <= 0) {
				continue;
			}
			g.pose().pushMatrix();
			g.pose().translate(cx, y);
			g.pose().scale(0.625f, 0.625f);
			g.item(RESOURCE_ICONS[k], 0, 0);
			g.pose().popMatrix();
			String s = String.valueOf(need[k]);
			boolean enough = eco == null || eco.stock(k) >= need[k];
			g.text(font, s, cx + 11, y + 2, enough ? C_TEXT : C_BAD);
			cx += 13 + font.width(s) + 4;
		}
	}

	private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.textWithWordWrap(font, Component.literal(text), 0, 0, (int) ((w - 16) / 0.75f), color);
		g.pose().popMatrix();
	}
}
