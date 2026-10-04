"""Pixel-art textures for stage 5 (factory kit, control desk, Gepard ammo box). Run: python3 tools/make_factory_textures.py"""
import os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'airdefense', 'textures')
random.seed(7)


def save(img, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    print('wrote', os.path.relpath(p, ROOT))


def noise(c, n=10):
    return tuple(max(0, min(255, v + random.randint(-n, n))) for v in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def rect(px, x0, y0, x1, y1, c, n=0):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            px[x, y] = noise(c, n) if n else c


# --- Gepard 35 mm ammunition box: olive steel box, stencil band, brass rounds sticking out of the open lid ---
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = img.load()
olive, dark, light = (86, 96, 52), (52, 60, 32), (120, 132, 74)
rect(px, 2, 7, 13, 14, olive, 6)
for x in range(2, 14):
    px[x, 14] = dark
    px[x, 7] = light
for y in range(7, 15):
    px[2, y] = light
    px[13, y] = dark
rect(px, 4, 10, 11, 11, (220, 200, 90))  # yellow stencil band
for x in (6, 9):
    px[x, 10] = (40, 40, 30)
    px[x, 11] = (40, 40, 30)
for i, x in enumerate(range(3, 13, 2)):  # rounds: brass case, red/black tip
    top = 2 + (i % 2)
    for y in range(top + 2, 7):
        px[x, y] = (206, 160, 60)
        px[x + 1, y] = (168, 124, 40)
    px[x, top + 1] = (170, 40, 30)
    px[x + 1, top + 1] = (130, 30, 22)
    px[x, top] = (60, 50, 40)
save(img, 'item', 'gepard_ammo.png')

# --- Factory kit: a wooden crate with a rolled-out blue blueprint of a factory on top ---
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = img.load()
wood, wood_d = (156, 116, 70), (110, 78, 44)
rect(px, 1, 8, 14, 15, wood, 8)
for x in range(1, 15):
    px[x, 8] = wood_d
    px[x, 15] = wood_d
    px[x, 11] = wood_d
for y in range(8, 16):
    px[1, y] = wood_d
    px[14, y] = wood_d
blue, line = (52, 92, 170), (210, 228, 255)
rect(px, 2, 1, 13, 7, blue, 4)
for x in range(2, 14):
    px[x, 1] = (34, 64, 130)
# saw-tooth roof outline on the blueprint
for i, x in enumerate(range(3, 13)):
    px[x, 6] = line
for x in (3, 6, 9, 12):
    px[x, 3] = line
    px[x, 4] = line
    px[x, 5] = line
for x in (4, 7, 10):
    px[x, 4] = line
for x in (5, 8, 11):
    px[x, 5] = line
px[11, 2] = line  # chimney
px[11, 3] = line
save(img, 'item', 'factory_kit.png')

# --- Control desk: front panel with a green screen, lamps and buttons; plain riveted sides ---
img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
px = img.load()
steel, edge = (96, 104, 112), (62, 68, 74)
rect(px, 0, 0, 15, 15, steel, 5)
for i in range(16):
    px[i, 0] = edge
    px[i, 15] = edge
    px[0, i] = edge
    px[15, i] = edge
rect(px, 2, 2, 13, 8, (18, 28, 22))
rect(px, 3, 3, 12, 7, (30, 96, 52), 6)
for x in range(3, 13):
    if random.random() < 0.6:
        px[x, 3 + random.randint(0, 4)] = (120, 240, 140)
for x, c in ((3, (230, 60, 50)), (6, (240, 200, 60)), (9, (80, 200, 90)), (12, (70, 140, 230))):
    px[x, 11] = c
    px[x, 12] = tuple(int(v * 0.7) for v in c)
rect(px, 2, 14, 13, 14, (40, 44, 48))
save(img, 'block', 'factory_controller_front.png')

img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
px = img.load()
rect(px, 0, 0, 15, 15, steel, 6)
for i in range(16):
    px[i, 0] = edge
    px[i, 15] = edge
    px[0, i] = edge
    px[15, i] = edge
for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
    px[x, y] = (150, 158, 166)
rect(px, 4, 6, 11, 9, (70, 76, 82))
for x in range(5, 11, 2):
    px[x, 7] = (30, 32, 34)
save(img, 'block', 'factory_controller_side.png')

img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
px = img.load()
rect(px, 0, 0, 15, 15, (84, 90, 96), 6)
for i in range(16):
    px[i, 0] = edge
    px[i, 15] = edge
    px[0, i] = edge
    px[15, i] = edge
rect(px, 3, 3, 12, 12, (110, 118, 126), 4)
save(img, 'block', 'factory_controller_top.png')
