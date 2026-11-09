"""Item textures: Staff of Ainz Ooal Gown (8 gem states, 32x32) and the Ring of Ainz Ooal Gown."""
import math, os
from PIL import Image

GEMS = [(230, 40, 50), (60, 110, 235), (60, 200, 90), (170, 70, 230), (250, 210, 60), (240, 240, 250), (60, 220, 230)]

def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height: img.putpixel((int(x), int(y)), tuple(c) + (255,))

def staff(gems):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    gold, gold_hi, gold_dk, outline = (222, 178, 60), (252, 226, 130), (150, 108, 30), (60, 40, 14)
    # shaft: bottom-left to the crown, a twisting gold rod
    for t in range(0, 26):
        x, y = 2 + t * 0.72, 29 - t * 0.72
        px(img, x - 1, y, outline); px(img, x + 1, y, outline); px(img, x, y + 1, outline)
        px(img, x, y, gold_hi if t % 4 == 0 else gold)
        if t % 4 == 2: px(img, x + 1, y - 1, gold_dk)
    # crown: seven serpents in a ring, each holding a gem
    cx, cy, r = 22, 9, 6.5
    for i in range(7):
        a = math.radians(-90 + i * (360 / 7))
        sx, sy = cx + math.cos(a) * r, cy + math.sin(a) * r
        # serpent body from centre to rim
        for k in range(0, 7):
            f = k / 6
            px(img, cx + math.cos(a) * r * f * 0.9, cy + math.sin(a) * r * f * 0.9, gold if k % 2 else gold_dk)
        px(img, sx, sy, outline)
        gx, gy = round(sx), round(sy)
        if i < gems:
            g = GEMS[i]
            px(img, gx, gy, g); px(img, gx + 1, gy, tuple(min(255, v + 60) for v in g))
            px(img, gx, gy + 1, tuple(int(v * .6) for v in g)); px(img, gx + 1, gy + 1, g)
        else:
            px(img, gx, gy, (40, 28, 16)); px(img, gx + 1, gy, (70, 50, 24))
            px(img, gx, gy + 1, (30, 20, 10)); px(img, gx + 1, gy + 1, (40, 28, 16))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1): px(img, cx + dx, cy + dy, gold_hi if (dx, dy) == (0, 0) else gold)
    if gems >= 7:   # awakened: purple aura
        for i in range(32):
            for j in range(32):
                if img.getpixel((i, j))[3] == 0 and math.hypot(i - cx, j - cy) < 10.5 and (i + j) % 3 == 0:
                    if any(img.getpixel((i + a, j + b))[3] > 0 for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= i + a < 32 and 0 <= j + b < 32):
                        img.putpixel((i, j), (170, 90, 255, 200))
    return img

def ring():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 9)
            if 3.6 < d < 6.0:
                img.putpixel((x, y), (252, 220, 110, 255) if (x + y) % 5 == 0 else (224, 176, 56, 255) if y < 9 else (170, 120, 30, 255))
    for x, y in ((6, 3), (7, 3), (8, 3), (9, 3), (6, 4), (9, 4), (7, 2), (8, 2)): img.putpixel((x, y), (120, 80, 20, 255))
    for x, y in ((7, 3), (8, 3), (7, 4), (8, 4)): img.putpixel((x, y), (210, 30, 50, 255))
    img.putpixel((7, 3), (255, 140, 150, 255))
    return img

def make(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for g in range(8): staff(g).save(f"{out_dir}/staff_of_ainz_ooal_gown_{g}.png")
    ring().save(f"{out_dir}/ring_of_ainz_ooal_gown.png")
