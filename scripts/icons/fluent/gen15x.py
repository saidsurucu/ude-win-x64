#!/usr/bin/env python3
"""@1.5x (kesirli %150 ekran) ikon varyanti uretici.

generate.py'nin SVG kurma mantigini yeniden kullanir; SADECE {res}@1.5x.png
(mantiksal boyut * 1.5) uretir -- mevcut 1x/@2x'e dokunmaz. Vektorden TAM 1.5x
boyuta resvg ile render edildigi icin %150 ekranda pixel-keskin (bicubic
downscale degil). Kullanim:
  gen15x.py <resvg.exe>            # tum (KEEP olmayan) ikonlar
  gen15x.py <resvg.exe> bold save # yalniz adi verilenler
"""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import generate as g

RESVG = sys.argv[1]
ONLY = set(sys.argv[2:])


def render15(svg: str, w: int, h: int, out: Path):
    fd, tmp = tempfile.mkstemp(suffix=".svg")
    try:
        os.write(fd, svg.encode("utf-8"))
        os.close(fd)
        subprocess.run([RESVG, "--width", str(w), "--height", str(h), tmp, str(out)],
                       check=True)
    finally:
        os.unlink(tmp)


def main():
    rows = g.load_mapping()
    made = 0
    for res, size, src, rule in rows:
        if src == "KEEP" or (ONLY and res not in ONLY):
            continue
        if size == "auto":
            w, h = g.png_size(g.OVERRIDES / f"{res}.png")
        else:
            w, h = (int(v) for v in size.lower().split("x"))
        if src.startswith("compose:"):
            svg = g.COMPOSE[src[8:]](rule)
        elif res in g.COMPOSE:
            svg = g.COMPOSE[res](rule)
        else:
            svg = g.recolor(g.fetch(src), rule)
            if w != h:
                svg = g.wrap_center(svg, w, h)
        w15, h15 = round(w * 1.5), round(h * 1.5)
        render15(svg, w15, h15, g.OVERRIDES / f"{res}@1.5x.png")
        made += 1
    print(f"{made} @1.5x ikon uretildi -> {g.OVERRIDES}")


if __name__ == "__main__":
    main()
