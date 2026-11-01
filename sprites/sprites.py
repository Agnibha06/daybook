"""Daybook pixel art. Source of truth for every sprite.

Run: python3 sprites.py  -> writes Sprites.kt, launcher/notification vectors, preview.png
"""
import sys
from PIL import Image, ImageDraw

PALETTE = {
    "w": "ECE8E1",  # cream
    "g": "9AA0AA",  # light grey
    "s": "5A6270",  # slate
    "d": "343B49",  # deep slate
    "k": "1A1E27",  # near-black
    "a": "F0A23B",  # amber
    "y": "F7D354",  # yellow
    "o": "E0745A",  # ember
    "r": "C8463C",  # red
    "l": "6FBF8B",  # leaf
    "e": "3F8A5E",  # pine
    "b": "7FA7E8",  # sky
    "n": "3D5A9E",  # navy
    "p": "9C8FE0",  # plum
    "m": "D58FC0",  # rose
    "t": "5FB8B0",  # teal
    "c": "8A5A3C",  # bark
    "h": "D9A46E",  # tan
}

S = {}

# ---------- Tabs (12x12) ----------
S["tab_today"] = [
    "....gggg....",
    "..ggwwwwgg..",
    ".gwwwwawwwg.",
    ".gwwwwawwwg.",
    "gwwwwwawwwwg",
    "gwwwwwaaawwg",
    "gwwwwwwwwwwg",
    "gwwwwwwwwwwg",
    ".gwwwwwwwwg.",
    ".gwwwwwwwwg.",
    "..ggwwwwgg..",
    "....gggg....",
]
S["tab_plan"] = [
    "....ssss....",
    ".hhhsddshhh.",
    ".hwwwwwwwwh.",
    ".hwlwggggwh.",
    ".hwwwwwwwwh.",
    ".hwawgggwwh.",
    ".hwwwwwwwwh.",
    ".hwbwggggwh.",
    ".hwwwwwwwwh.",
    ".hwwwwwwwwh.",
    ".hhhhhhhhhh.",
    "............",
]
S["tab_climb"] = [
    ".....goo....",
    ".....gooo...",
    ".....g......",
    ".....w......",
    "....www.....",
    "...wwsss....",
    "..wssssss...",
    "..sssssssd..",
    ".ssssssssdd.",
    ".sssssssssdd",
    "ssssssssssdd",
    "dddddddddddd",
]
S["tab_insights"] = [
    "............",
    ".........aa.",
    ".........aa.",
    ".....bb..aa.",
    ".....bb..aa.",
    ".....bb..aa.",
    ".ll..bb..aa.",
    ".ll..bb..aa.",
    ".ll..bb..aa.",
    ".ll..bb..aa.",
    "gggggggggggg",
    "............",
]
S["tab_log"] = [
    "..g......g..",
    ".rgrrrrrrgr.",
    ".rrrrrrrrrr.",
    ".wwwwwwwwww.",
    ".wlwwawwwbw.",
    ".wwwwwwwwww.",
    ".wawwlwwlww.",
    ".wwwwwwwwww.",
    ".wlwwwwawww.",
    ".wwwwwwwwww.",
    ".gggggggggg.",
    "............",
]
S["gear"] = [
    ".....gg.....",
    "..gg.gg.gg..",
    "..gggggggg..",
    "...gggggg...",
    ".gggg..gggg.",
    "ggggg..ggggg",
    "ggggg..ggggg",
    ".gggg..gggg.",
    "...gggggg...",
    "..gggggggg..",
    "..gg.gg.gg..",
    ".....gg.....",
]

# ---------- Categories (10x10) ----------
S["cat_study"] = [
    "..........",
    ".nnnnnnnn.",
    ".nbbbbbbn.",
    ".nbwwwwbn.",
    ".nbbbbbbn.",
    ".nbbbbbbn.",
    ".nbbbbbbn.",
    ".nbbbbbbn.",
    ".nwwwwwwwn",
    "..nnnnnnn.",
]
S["cat_class"] = [
    "cccccccccc",
    "ceeeeeeeec",
    "cewwweeeec",
    "ceeeewweec",
    "cewweeeeec",
    "ceeeeewwec",
    "cccccccccc",
    "..c....c..",
    ".c......c.",
    "..........",
]
S["cat_lab"] = [
    "...wwww...",
    "....gg....",
    "....gg....",
    "....gg....",
    "...gttg...",
    "..gttttg..",
    ".gttwtttg.",
    ".gtttttwg.",
    ".gggggggg.",
    "..........",
]
S["cat_project"] = [
    "..........",
    ".gggggggg.",
    ".gkkkkkkg.",
    ".gkaakkkg.",
    ".gkkkllkg.",
    ".gkkkkkkg.",
    ".gggggggg.",
    "ssssssssss",
    ".dddddddd.",
    "..........",
]
S["cat_workout"] = [
    "..........",
    "..........",
    ".rr....rr.",
    "rrr....rrr",
    "rrrggggrrr",
    "rrrggggrrr",
    "rrr....rrr",
    ".rr....rr.",
    "..........",
    "..........",
]
S["cat_music"] = [
    "...mmmmmm.",
    "...mmmmmm.",
    "...m....m.",
    "...m....m.",
    "...m....m.",
    ".mmm..mmm.",
    "mmmm.mmmm.",
    "mmmm.mmmm.",
    ".mm...mm..",
    "..........",
]
S["cat_errand"] = [
    "..........",
    "...cccc...",
    "..c....c..",
    ".hhhhhhhh.",
    ".hhhhhhhh.",
    ".hhwwhhhh.",
    ".hhhhhhhh.",
    ".hhhhhhhh.",
    ".cccccccc.",
    "..........",
]
S["cat_break"] = [
    "..g..g....",
    "...g..g...",
    "..g..g....",
    ".wwwwwww..",
    ".wcccccwww",
    ".wcccccw.w",
    ".wcccccwww",
    "..wwwww...",
    "gggggggggg",
    "..........",
]

# ---------- Climber avatar (12x14, two frames) ----------
S["climber_a"] = [
    "....aaaa....",
    "...aayaaa...",
    "...aaaaaa...",
    "...hhhhhh...",
    "...hkhhkh...",
    "...hhhhhh...",
    "..oobbbbbb..",
    ".ooobbbbbbh.",
    ".ooobbbbbb..",
    "..oobbbbbb..",
    "...nnnnnn...",
    "...nn..nn...",
    "...nn..nn...",
    "..ccc..ccc..",
]
S["climber_b"] = [
    "............",
    "....aaaa....",
    "...aayaaa...",
    "...aaaaaa...",
    "...hhhhhh...",
    "...hkhhkh...",
    "...hhhhhh...",
    "..oobbbbbbh.",
    ".ooobbbbbb..",
    ".ooobbbbbb..",
    "..oobbbbbb..",
    "...nnnnnn...",
    "..nn....nn..",
    ".ccc....ccc.",
]

# ---------- Map pieces ----------
S["stone_done"] = [
    "..aaaa..",
    ".ayyaaa.",
    "aayaaaaa",
    "aaaaaaaa",
    "aaaaaaao",
    "aaaaaaoo",
    ".aaaaoo.",
    "..oooo..",
]
S["stone_todo"] = [
    "..ssss..",
    ".sggsss.",
    "ssgsssss",
    "ssssssss",
    "ssssssd.",
    "sssssddd",
    ".sssddd.",
    "..dddd..",
]
S["tent"] = [
    "......r.......",
    "......rrr.....",
    "......rrrr....",
    "......r.......",
    "......aa......",
    ".....aaaa.....",
    "....aaaoaa....",
    "...aaaoooaa...",
    "..aaaaoooaaa..",
    ".aaaaaoooaaaa.",
    "aaaaaaoooaaaaa",
    "cccccccccccccc",
]
S["tent_todo"] = [
    "..............",
    "..............",
    "..............",
    "..............",
    "......ss......",
    ".....ssss.....",
    "....sssdss....",
    "...sssdddss...",
    "..ssssdddsss..",
    ".sssssdddssss.",
    "ssssssdddsssss",
    "dddddddddddddd",
]
S["pine"] = [
    "...ee...",
    "...ee...",
    "..eeee..",
    "..elee..",
    ".eeeeee.",
    ".eleeee.",
    "eeeeeeee",
    "eeeeleee",
    "...cc...",
    "...cc...",
]
S["base"] = [
    "....ww........",
    "...wwww...cc..",
    "..wwrrww..cc..",
    ".wwrrrrww.....",
    "wwwwwwwwww....",
    ".wwnnwwww.....",
    ".wwnnwwww.hhh.",
    ".wwnnwwww.hhh.",
    "cccccccccccccc",
]

# ---------- Flame (focus candle), 3 frames 6x8 ----------
S["flame_a"] = [
    "..y...",
    "..yy..",
    ".yyy..",
    ".yaay.",
    "yaaoay",
    "yaooay",
    ".aooa.",
    "..ww..",
]
S["flame_b"] = [
    "...y..",
    "..yy..",
    "..yyy.",
    ".yaay.",
    ".yaoay",
    "yaooay",
    ".aooa.",
    "..ww..",
]
S["flame_c"] = [
    "......",
    "..y...",
    ".yyy..",
    ".yayy.",
    "yaaoay",
    "yaooay",
    ".aooa.",
    "..ww..",
]

# ---------- Moods (8x8): 1 rough .. 5 great ----------
S["mood_1"] = [
    "..ssss..",
    ".ssssss.",
    "sskssks.",
    "ssssssss",
    "ssssssss",
    "sskkkkss",
    ".kssssk.",
    "..ssss..",
]
S["mood_1"][2] = "ssksskss"
S["mood_1"][3] = "ssssssbs"
S["mood_2"] = [
    "..bbbb..",
    ".bbbbbb.",
    "bbkbbkbb",
    "bbbbbbbb",
    "bbbbbbbb",
    "bbbbkkbb",
    ".bkkbbb.",
    "..bbbb..",
]
S["mood_3"] = [
    "..pppp..",
    ".pppppp.",
    "ppkppkpp",
    "pppppppp",
    "pppppppp",
    "ppkkkkpp",
    ".pppppp.",
    "..pppp..",
]
S["mood_4"] = [
    "..llll..",
    ".llllll.",
    "llkllkll",
    "llllllll",
    "lkllllkl",
    "llkkkkll",
    ".llllll.",
    "..llll..",
]
S["mood_5"] = [
    "..aaaa..",
    ".aaaaaa.",
    "aakaakaa",
    "aaaaaaaa",
    "akkkkkka",
    "aakyykaa",
    ".aakkaa.",
    "..aaaa..",
]

# ---------- Badges (12x12) ----------
S["badge_first"] = [  # boot
    "............",
    "...cccc.....",
    "...chhc.....",
    "...chhc.....",
    "...chhc.....",
    "...chhcc....",
    "...chhhhcc..",
    "..cchhhhhhc.",
    "..chhhhhhhc.",
    "..cccccccccc",
    "..kkkkkkkkkk",
    "............",
]
S["badge_week"] = [
    ".....r......",
    ".....rrr....",
    ".....r......",
    ".....aa.....",
    "....aaaa....",
    "...aaooaa...",
    "..aaaooaaa..",
    ".aaaaooaaaa.",
    "aaaaaooaaaaa",
    "cccccccccccc",
    "............",
    "............",
]
S["badge_fortnight"] = [  # double flag
    "............",
    ".gbbb..goo..",
    ".gbbbb.gooo.",
    ".gbb...goo..",
    ".g.....g....",
    ".g.....g....",
    ".g.....g....",
    ".g.....g....",
    ".g.....g....",
    "ddddddddddd.",
    "............",
    "............",
]
S["badge_month"] = [  # sun over peak
    "......yy....",
    "....yyyyyy..",
    "...yyyyyyyy.",
    "....yyyyyy..",
    "....w.......",
    "...www......",
    "..wwsss.....",
    ".wssssss....",
    ".sssssssd...",
    "sssssssssdd.",
    "ssssssssssdd",
    "dddddddddddd",
]
S["badge_early"] = [  # moon
    "....wwww....",
    "..wwwww.....",
    ".wwwww......",
    ".wwww....y..",
    "wwwww.......",
    "wwwww.......",
    "wwwwww......",
    ".wwwwww.....",
    ".wwwwwwww.y.",
    "..wwwwwwwww.",
    "....wwwww...",
    "............",
]
S["badge_ghost"] = [
    "....wwww....",
    "..wwwwwwww..",
    ".wwwwwwwwww.",
    ".wwkkwwkkww.",
    ".wwkkwwkkww.",
    ".wwwwwwwwww.",
    ".wwwwkkwwww.",
    ".wwwwwwwwww.",
    ".wwwwwwwwww.",
    ".wwwwwwwwww.",
    ".ww.ww.ww.w.",
    ".w...w...w..",
]
S["badge_deep"] = [  # diamond
    "............",
    "...bbbbbb...",
    "..bwbbbbnb..",
    ".bwbbbbbbnb.",
    "bbbbbbbbbbnn",
    ".bbbbbbbbnn.",
    "..bbbbbbnn..",
    "...bbbbnn...",
    "....bbnn....",
    ".....nn.....",
    "............",
    "............",
]
S["badge_sweep"] = [  # star
    ".....yy.....",
    ".....yy.....",
    "....yyyy....",
    "yyyyyyyyyyyy",
    ".yyyyyyyyyy.",
    "..yyyyyyyy..",
    "...yyyyyy...",
    "...yyyyyy...",
    "..yyy..yyy..",
    "..yy....yy..",
    ".yy......yy.",
    "............",
]
S["badge_shield"] = [
    "..pppppppp..",
    ".pwwppppppp.",
    ".pwppppppppd",
    ".ppppwwppppd",
    ".pppwwwwpppd",
    ".ppppwwppppd",
    ".ppppwwppppd",
    "..pppppppdd.",
    "..ppppppppd.",
    "...ppppppd..",
    "....ppppd...",
    ".....pd.....",
]
S["badge_iron"] = [  # dumbbell + bar
    "............",
    "............",
    ".rr......rr.",
    "rrr......rrr",
    "rrrggggggrrr",
    "rrrggggggrrr",
    "rrr......rrr",
    ".rr......rr.",
    "............",
    "...l.l.l.l..",
    "............",
    "............",
]
S["badge_steady"] = [  # pin
    "....rrrr....",
    "...rrwwrr...",
    "..rrwrrrrr..",
    "..rrrrrrrr..",
    "..rrrrrrrr..",
    "...rrrrrr...",
    "....rrrr....",
    ".....gg.....",
    ".....gg.....",
    ".....gg.....",
    ".....g......",
    "............",
]
S["badge_coin"] = [
    "....yyyy....",
    "..yyaaaayy..",
    ".yaayyyyaay.",
    ".yayyaayyay.",
    "yayyaayyyaay",
    "yayyaayyyaay",
    "yayyaayyyaay",
    "yayyaayyyaay",
    ".yayaaaayay.",
    ".yaayyyyaay.",
    "..yyaaaayy..",
    "....yyyy....",
]
S["badge_lock"] = [  # padlock (locked-in before 1 AM streak)
    "....gggg....",
    "...g....g...",
    "...g....g...",
    "...g....g...",
    "..aaaaaaaa..",
    "..ayyyyyya..",
    "..aaakkaaa..",
    "..aaakkaaa..",
    "..aaaakaaa..",
    "..aaaaaaaa..",
    "..oooooooo..",
    "............",
]


# ---------- Sleep and wake ----------
S["sun_rise"] = [
    "..y....yy....y..",
    "...y...yy...y...",
    "................",
    "......aaaa......",
    "....aayyyyaa....",
    "...ayyyyyyyya...",
    "y..ayyyyyyyya..y",
    "..ayyyyyyyyyya..",
    "oooooooooooooooo",
    "..o.oo..ooo.o...",
    "dddddddddddddddd",
    "................",
]
S["bed"] = [
    "cc............",
    "cc............",
    "ccwww.........",
    "ccwwwnnnnnnnnn",
    "ccnnnnnnnnnnnn",
    "ccnnbnnnbnnnnn",
    "cccccccccccccc",
    "cc..........cc",
    "cc..........cc",
]
S["zzz"] = [
    ".....bbbb",
    ".......b.",
    "bbb...b..",
    "..b..bbbb",
    ".b.......",
    "bbb......",
]
S["alarm"] = [
    ".rr......rr.",
    "rrr.gggg.rrr",
    "rr.gwwwwg.rr",
    "..gwwwawwg..",
    ".gwwwwawwwg.",
    ".gwwwwaaawg.",
    ".gwwwwwwwwg.",
    ".gwwwwwwwwg.",
    "..gwwwwwwg..",
    "...gggggg...",
    "..g......g..",
    ".g........g.",
]
S["badge_rested"] = [
    "........bbbb",
    "..........b.",
    ".........b..",
    "........bbbb",
    "............",
    "..wwwwwwww..",
    ".wwwwwwwwww.",
    "wwwgwwwwgwww",
    "wwwwwwwwwwww",
    ".wwwwwwwwww.",
    "..gggggggg..",
    "............",
]

# ---------- Interface glyphs (7x7) ----------
_up = [
    "...w...",
    "..www..",
    ".wwwww.",
    "wwwwwww",
    "..www..",
    "..www..",
    "..www..",
]
S["arrow_up"] = _up
S["arrow_down"] = list(reversed(_up))
S["arrow_left"] = ["".join(_up[r][c] for r in range(7)) for c in range(7)]
S["arrow_right"] = [row[::-1] for row in S["arrow_left"]]
S["plus"] = ["..www..", "..www..", "wwwwwww", "wwwwwww", "wwwwwww", "..www..", "..www.."]
S["check"] = [
    ".......l",
    "......ll",
    "l....ll.",
    "ll..ll..",
    ".llll...",
    "..ll....",
]
S["cross"] = [
    "oo...oo",
    "ooo.ooo",
    ".ooooo.",
    "..ooo..",
    ".ooooo.",
    "ooo.ooo",
    "oo...oo",
]

# ---------- Launcher (16x16) ----------
S["launcher"] = [
    "................",
    ".....goo...yyy..",
    ".....gooo.yyyyy.",
    ".....g....yyyyy.",
    ".....g.....yyy..",
    ".....w..........",
    "....www.........",
    "...wwsss....d...",
    "..wssssss..ddd..",
    "..sssssssddddd..",
    ".sssssssssddddd.",
    ".ssssssssssdddd.",
    "ssssssssssssdddd",
    "ssssssssssssssdd",
    "eeeeeeeeeeeeeeee",
    "................",
]

# ---------------------------------------------------------------------------

def check():
    ok = True
    for name, rows in S.items():
        w = len(rows[0])
        for i, r in enumerate(rows):
            if len(r) != w:
                print(f"{name}: row {i} width {len(r)} != {w}: {r!r}")
                ok = False
            for ch in r:
                if ch != "." and ch not in PALETTE:
                    print(f"{name}: unknown colour {ch!r}")
                    ok = False
    return ok


def kotlin():
    out = [
        "package com.agnibha.tracker.ui",
        "",
        "// Generated by sprites.py. Edit the Python source, not this file.",
        "",
        "object Sprites {",
    ]
    for name, rows in S.items():
        camel = "".join(p if i == 0 else p.capitalize() for i, p in enumerate(name.split("_")))
        body = ", ".join(f'"{r}"' for r in rows)
        out.append(f"    val {camel} = Sprite({body})")
    out.append("}")
    out.append("")
    out.append("object PixelPalette {")
    out.append("    val colors: Map<Char, Long> = mapOf(")
    for ch, hexv in PALETTE.items():
        out.append(f"        '{ch}' to 0xFF{hexv},")
    out.append("    )")
    out.append("}")
    return "\n".join(out) + "\n"


def vector(rows, px, offset, mono=None):
    """Android vector drawable: one path per colour, one square per pixel."""
    w = len(rows[0])
    h = len(rows)
    size = 108 if mono is None else 24
    if mono is not None:
        px = 24 / max(w, h)
        offset = (24 - w * px) / 2
    paths = {}
    for y, row in enumerate(rows):
        x = 0
        while x < w:
            ch = row[x]
            if ch == ".":
                x += 1
                continue
            run = 1
            while x + run < w and row[x + run] == ch:
                run += 1
            key = mono if mono else ch
            X = offset + x * px
            Y = offset + y * px
            paths.setdefault(key, []).append(f"M{X:g},{Y:g}h{run * px:g}v{px:g}h{-run * px:g}z")
            x += run
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{size}dp"',
        f'    android:height="{size}dp"',
        f'    android:viewportWidth="{size}"',
        f'    android:viewportHeight="{size}">',
    ]
    for key, segs in paths.items():
        color = "#FFFFFFFF" if mono else "#FF" + PALETTE[key]
        lines.append(f'    <path android:fillColor="{color}" android:pathData="{"".join(segs)}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def preview(path):
    scale = 6
    names = list(S.keys())
    cols = 8
    cell = 20 * scale
    rows_n = (len(names) + cols - 1) // cols
    img = Image.new("RGB", (cols * cell, rows_n * (cell + 14)), "#0E1016")
    d = ImageDraw.Draw(img)
    for i, name in enumerate(names):
        rows = S[name]
        ox = (i % cols) * cell + 6
        oy = (i // cols) * (cell + 14) + 6
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                d.rectangle([ox + x * scale, oy + y * scale, ox + (x + 1) * scale - 1, oy + (y + 1) * scale - 1], fill="#" + PALETTE[ch])
        d.text((ox, oy + cell - 8), name, fill="#9AA0AA")
    img.save(path)


if __name__ == "__main__":
    if not check():
        sys.exit(1)
    root = sys.argv[1]
    open(f"{root}/app/src/main/java/com/agnibha/tracker/ui/Sprites.kt", "w").write(kotlin())
    # Launcher: 16 px sprite at 4 units per pixel inside the 66-unit safe zone.
    open(f"{root}/app/src/main/res/drawable/ic_launcher_fg.xml", "w").write(vector(S["launcher"], 4, 22))
    open(f"{root}/app/src/main/res/drawable/ic_stat.xml", "w").write(vector(S["tab_climb"], 0, 0, mono="w"))
    preview(f"{root}/sprites/preview.png")
    print("ok", len(S), "sprites")
