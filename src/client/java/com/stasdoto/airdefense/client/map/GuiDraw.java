package com.stasdoto.airdefense.client.map;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Lines and circles for the map and the radar (1.25.1): each straight piece is one turned rectangle, not a row of
 * one-pixel squares (a long line was hundreds of them, a big circle up to 900 - the screens stuttered).
 */
final class GuiDraw {
	/** Where pieces may be drawn: a rectangle, and optionally a circle inside it (the radar scope). */
	record Clip(double x0, double y0, double x1, double y1, double cx, double cy, double r) {
		static Clip rect(double x0, double y0, double x1, double y1) {
			return new Clip(x0, y0, x1, y1, 0, 0, -1);
		}

		static Clip disc(double cx, double cy, double r) {
			return new Clip(cx - r, cy - r, cx + r, cy + r, cx, cy, r);
		}

		boolean inside(double x, double y) {
			if (x < x0 || y < y0 || x > x1 || y > y1) {
				return false;
			}
			return r < 0 || (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
		}
	}

	private GuiDraw() {
	}

	/** A straight line {@code width} pixels thick; {@code dash} > 0: dashes and gaps that long. */
	static void line(GuiGraphicsExtractor g, Clip clip, double x0, double y0, double x1, double y1, float width, int color, int dash) {
		double[] c = clipToRect(clip, x0, y0, x1, y1);
		if (c == null) {
			return;
		}
		if (clip.r() >= 0) {
			c = clipToDisc(clip, c[0], c[1], c[2], c[3]);
			if (c == null) {
				return;
			}
		}
		if (dash <= 0) {
			piece(g, c[0], c[1], c[2], c[3], width, color);
			return;
		}
		double len = Math.hypot(c[2] - c[0], c[3] - c[1]);
		if (len < 1e-3) {
			return;
		}
		double ux = (c[2] - c[0]) / len;
		double uy = (c[3] - c[1]) / len;
		for (double s = 0; s < len; s += dash * 2) {
			double e = Math.min(len, s + dash);
			piece(g, c[0] + ux * s, c[1] + uy * s, c[0] + ux * e, c[1] + uy * e, width, color);
		}
	}

	/** A circle as straight pieces (dashed when {@code dash} > 0), only the pieces inside the clip. */
	static void circle(GuiGraphicsExtractor g, Clip clip, double cx, double cy, double r, float width, int color, int dash) {
		if (r < 1) {
			return;
		}
		// Off the clip entirely?
		if (cx + r < clip.x0() || cx - r > clip.x1() || cy + r < clip.y0() || cy - r > clip.y1()) {
			return;
		}
		int n = (int) Mth.clamp(r * Mth.TWO_PI / (dash > 0 ? dash : 5), 24, 360);
		for (int i = 0; i < n; i++) {
			if (dash > 0 && (i & 1) == 1) {
				continue;
			}
			double a0 = Mth.TWO_PI * i / n;
			double a1 = Mth.TWO_PI * (i + 1) / n;
			double x0 = cx + Math.cos(a0) * r;
			double y0 = cy + Math.sin(a0) * r;
			double x1 = cx + Math.cos(a1) * r;
			double y1 = cy + Math.sin(a1) * r;
			if (clip.inside((x0 + x1) / 2, (y0 + y1) / 2)) {
				piece(g, x0, y0, x1, y1, width, color);
			}
		}
	}

	/** One turned rectangle from (x0, y0) to (x1, y1). */
	private static void piece(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, float width, int color) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		float len = (float) Math.hypot(dx, dy);
		if (len < 0.5f) {
			g.fill((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.floor(x0) + 1, (int) Math.floor(y0) + 1, color);
			return;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) x0, (float) y0);
		pose.rotate((float) Math.atan2(dy, dx));
		// The rectangle is laid along the x axis; fill() takes whole numbers, so it is drawn at 16x and scaled down.
		pose.scale(1 / 16f, 1 / 16f);
		int l = Math.round(len * 16);
		int h = Math.max(8, Math.round(width * 16));
		g.fill(0, -h / 2, l, h - h / 2, color);
		pose.popMatrix();
	}

	/** The part of the segment inside the clip's rectangle (Liang-Barsky), or null. */
	private static double[] clipToRect(Clip c, double x0, double y0, double x1, double y1) {
		double t0 = 0;
		double t1 = 1;
		double dx = x1 - x0;
		double dy = y1 - y0;
		double[] p = {-dx, dx, -dy, dy};
		double[] q = {x0 - c.x0(), c.x1() - x0, y0 - c.y0(), c.y1() - y0};
		for (int i = 0; i < 4; i++) {
			if (Math.abs(p[i]) < 1e-12) {
				if (q[i] < 0) {
					return null;
				}
				continue;
			}
			double t = q[i] / p[i];
			if (p[i] < 0) {
				t0 = Math.max(t0, t);
			} else {
				t1 = Math.min(t1, t);
			}
			if (t0 > t1) {
				return null;
			}
		}
		return new double[]{x0 + dx * t0, y0 + dy * t0, x0 + dx * t1, y0 + dy * t1};
	}

	/** The part of the segment inside the clip's circle, or null. */
	private static double[] clipToDisc(Clip c, double x0, double y0, double x1, double y1) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		double fx = x0 - c.cx();
		double fy = y0 - c.cy();
		double a = dx * dx + dy * dy;
		if (a < 1e-12) {
			return c.inside(x0, y0) ? new double[]{x0, y0, x1, y1} : null;
		}
		double b = 2 * (fx * dx + fy * dy);
		double cc = fx * fx + fy * fy - c.r() * c.r();
		double disc = b * b - 4 * a * cc;
		if (disc < 0) {
			return null;
		}
		double sq = Math.sqrt(disc);
		double t0 = Math.max(0, (-b - sq) / (2 * a));
		double t1 = Math.min(1, (-b + sq) / (2 * a));
		if (t0 >= t1) {
			return null;
		}
		return new double[]{x0 + dx * t0, y0 + dy * t0, x0 + dx * t1, y0 + dy * t1};
	}
}
