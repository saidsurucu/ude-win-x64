#!/usr/bin/env python3
"""Fluent ikon üretici (UDE mac-arm).

mapping.tsv satırlarından scripts/icons/overrides/resources/ altına
native + @2x PNG üretir. Kullanım:
  generate.py                # mapping'deki tüm (KEEP olmayan) ikonlar
  generate.py bold italic    # yalnız adı verilenler
  generate.py --check        # fluent adlarının CDN'de varlığını doğrula
  generate.py --dump <fluent_adı>  # alt-yol (subpath) parçalarını listele

mapping.tsv formatı (TAB ayraçlı, '#' yorum):
  resource<TAB>WxH|auto<TAB>fluent_adı|KEEP|compose:<ad><TAB>renk_kuralı
renk kuralı: all=#hex | body=#hex[;sub:N=#hex]... [;extra:<path-d>=#hex]
  sub:N  -> tüm <path> d'leri büyük-M sınırlarından bölündükten sonraki N. parça
  extra  -> verilen path d'si verilen renkle EN ALTA (zemine) eklenir
"""
import re
import struct
import subprocess
import sys
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
OVERRIDES = HERE.parent / "overrides" / "resources"
CACHE = HERE / "cache"
MAPPING = HERE / "mapping.tsv"
CDN = "https://cdn.jsdelivr.net/npm/@fluentui/svg-icons/icons/{}.svg"
SKIP_2X = {"search"}  # CLAUDE.md: search@2x tam pikselde çizilip kırpılıyor


def png_size(path: Path):
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"PNG değil: {path}")
    w, h = struct.unpack(">II", head[16:24])
    return w, h


def fetch(fluent_name: str) -> str:
    CACHE.mkdir(exist_ok=True)
    p = CACHE / (fluent_name + ".svg")
    if not p.exists():
        with urllib.request.urlopen(CDN.format(fluent_name), timeout=30) as r:
            p.write_bytes(r.read())
    return p.read_text()


def paths_of(svg: str):
    return re.findall(r'<path[^>]*\bd="([^"]+)"', svg)


def subpath_chunks(svg: str):
    """Tüm path d'lerini büyük-M sınırlarından parçalara böl (sıralı, düz liste).
    Küçük-m ile süren alt-yollar önceki parçaya yapışık kalır (koordinat güvenliği)."""
    chunks = []
    for d in paths_of(svg):
        parts = [p for p in re.split(r"(?=M)", d) if p.strip()]
        chunks.extend(parts)
    return chunks


def parse_rule(rule: str):
    body, subs, extras = "#444444", {}, []
    for part in rule.split(";"):
        part = part.strip()
        if not part:
            continue
        k, _, v = part.partition("=")
        if k == "all" or k == "body":
            body = v
        elif k.startswith("sub:"):
            subs[int(k[4:])] = v
        elif k.startswith("extra:"):
            extras.append((k[6:], v))
        else:
            raise ValueError(f"bilinmeyen kural: {part}")
    return body, subs, extras


def recolor(svg: str, rule: str) -> str:
    """24x24 fluent SVG'yi kurala göre çok-path'li renkli SVG'ye çevirir.

    DİKKAT: alt-yollar ayrı <path>'lere bölününce dolgu kuralı (winding)
    bozulur, iç delikler som dolguya döner. Bu yüzden sub: kullanılmıyorsa
    path'ler olduğu gibi boyanır; sub: varken aynı renkteki ARDIŞIK parçalar
    tek path'te birleştirilir (delikler renk grubu içinde korunur)."""
    body, subs, extras = parse_rule(rule)
    m = re.search(r'viewBox="([^"]+)"', svg)
    viewbox = m.group(1) if m else "0 0 24 24"
    paths = []
    for d, color in extras:  # zemine
        paths.append(f'<path fill="{color}" d="{d}"/>')
    if subs:
        chunks = subpath_chunks(svg)
        i = 0
        while i < len(chunks):
            color = subs.get(i, body)
            j = i
            while j < len(chunks) and subs.get(j, body) == color:
                j += 1
            d = "".join(chunks[i:j])
            paths.append(f'<path fill="{color}" d="{d}"/>')
            i = j
    else:
        for d in paths_of(svg):
            paths.append(f'<path fill="{body}" d="{d}"/>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}">'
            + "".join(paths) + "</svg>")


def wrap_center(inner_svg: str, W: int, H: int) -> str:
    """Kare glyph'i WxH tuvale orantı bozmadan ortalar."""
    side = min(W, H)
    x, y = (W - side) // 2, (H - side) // 2
    inner = inner_svg.replace("<svg ", f'<svg x="{x}" y="{y}" width="{side}" height="{side}" ', 1)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
            f'viewBox="0 0 {W} {H}">{inner}</svg>')


def render(svg: str, W: int, H: int, out: Path):
    subprocess.run(["rsvg-convert", "-w", str(W), "-h", str(H), "-o", str(out)],
                   input=svg.encode(), check=True)


# ---- elle kompozisyonlar: ad -> f(rule) -> tam SVG (viewBox hedef orana uygun) ----
def compose_search(rule: str) -> str:
    """search.png: 24x24 tuval, ~10px glyph +7+7 (görünür pencere satır 6-17)."""
    glyph = recolor(fetch("search_24_regular"), rule)
    inner = glyph.replace("<svg ", '<svg x="7" y="7" width="10" height="10" ', 1)
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" '
            f'viewBox="0 0 24 24">{inner}</svg>')


def compose_glyph_chevron(glyph_name):
    """42x24 liste butonu: glyph solda, küçük chevron sağda. Şerit buton
    yuvası ~25 logical px; 26'lık tuval glifi alta itip altdan kırptırıyordu
    (piksel ölçümü), 24'e küçültülüp içerik ortalandı."""
    def f(rule):
        g = recolor(fetch(glyph_name), rule)
        ch = recolor(fetch("chevron_down_24_regular"), "all=#444444")
        gi = g.replace("<svg ", '<svg x="1" y="2" width="20" height="20" ', 1)
        ci = ch.replace("<svg ", '<svg x="28" y="6" width="12" height="12" ', 1)
        return ('<svg xmlns="http://www.w3.org/2000/svg" width="42" height="24" '
                f'viewBox="0 0 42 24">{gi}{ci}</svg>')
    return f


COMPOSE = {
    "search": compose_search,
    "list_bullet_chevron": compose_glyph_chevron("text_bullet_list_ltr_24_regular"),
    "list_number_chevron": compose_glyph_chevron("text_number_list_ltr_24_regular"),
}


def load_mapping():
    rows = []
    for ln in MAPPING.read_text().splitlines():
        ln = ln.rstrip()
        if not ln or ln.startswith("#"):
            continue
        cols = ln.split("\t")
        if len(cols) != 4:
            raise ValueError(f"4 sütun bekleniyor: {ln!r}")
        rows.append(cols)
    return rows


def main():
    args = sys.argv[1:]
    if args[:1] == ["--dump"]:
        for i, c in enumerate(subpath_chunks(fetch(args[1]))):
            print(f"sub:{i}  {c[:70]}")
        return
    rows = load_mapping()
    if args[:1] == ["--check"]:
        bad = 0
        names = {r[2] for r in rows if r[2] not in ("KEEP",) and not r[2].startswith("compose:")}
        for n in sorted(names):
            try:
                fetch(n)
            except Exception as e:
                print(f"YOK: {n} ({e})")
                bad += 1
        print(f"{len(names)} ad, {bad} eksik")
        sys.exit(1 if bad else 0)
    only = set(args)
    made = 0
    for res, size, src, rule in rows:
        if src == "KEEP" or (only and res not in only):
            continue
        if size == "auto":
            W, H = png_size(OVERRIDES / f"{res}.png")
        else:
            W, H = (int(v) for v in size.lower().split("x"))
        if src.startswith("compose:"):
            svg = COMPOSE[src[8:]](rule)
        elif res in COMPOSE:
            svg = COMPOSE[res](rule)
        else:
            svg = recolor(fetch(src), rule)
            if W != H:
                svg = wrap_center(svg, W, H)
        render(svg, W, H, OVERRIDES / f"{res}.png")
        if res not in SKIP_2X:
            render(svg, W * 2, H * 2, OVERRIDES / f"{res}@2x.png")
        elif (OVERRIDES / f"{res}@2x.png").exists():
            (OVERRIDES / f"{res}@2x.png").unlink()
        made += 1
    print(f"{made} ikon üretildi -> {OVERRIDES}")


if __name__ == "__main__":
    main()
