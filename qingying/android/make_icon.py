from PIL import Image, ImageDraw

BASE = 1024
BG = (14, 22, 34)
TOP = (79, 140, 255)     # accent
BOT = (127, 180, 255)    # accent2


def gradient_circle(size, cx, cy, r):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    for y in range(size):
        t = y / max(1, size - 1)
        c = tuple(int(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3)) + (255,)
        gd.line([(0, y), (size, y)], fill=c)
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    layer = Image.composite(grad, Image.new("RGBA", (size, size), (0, 0, 0, 0)), mask)
    return layer


def draw_play(img, size, cx, cy, r):
    d = ImageDraw.Draw(img)
    # 白色圆角三角形
    s = r * 0.52
    x = cx + s * 0.18
    pts = [(x - s * 0.55, cy - s), (x - s * 0.55, cy + s), (x + s * 0.75, cy)]
    d.polygon(pts, fill=(255, 255, 255, 255))
    return img


def legacy(size):
    img = Image.new("RGBA", (BASE, BASE), BG + (255,))
    circle = gradient_circle(BASE, BASE // 2, BASE // 2, int(BASE * 0.34))
    img = Image.alpha_composite(img, circle)
    img = draw_play(img, BASE, BASE // 2, BASE // 2, int(BASE * 0.34))
    return img.resize((size, size), Image.LANCZOS)


def foreground(size):
    img = Image.new("RGBA", (BASE, BASE), (0, 0, 0, 0))
    circle = gradient_circle(BASE, BASE // 2, BASE // 2, int(BASE * 0.26))
    img = Image.alpha_composite(img, circle)
    img = draw_play(img, BASE, BASE // 2, BASE // 2, int(BASE * 0.26))
    return img.resize((size, size), Image.LANCZOS)


LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

for density, size in LEGACY.items():
    legacy(size).save(f"/workspace/qingying/android/res/mipmap-{density}/ic_launcher.png")

for density, size in FG.items():
    foreground(size).save(f"/workspace/qingying/android/res/mipmap-{density}/ic_launcher_foreground.png")

print("icons generated")
