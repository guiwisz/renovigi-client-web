#!/usr/bin/env python3
"""Gera as animações Lottie da cena solar (formato Bodymovin 5.x).

Saídas em app/src/main/res/raw/:
  - sky_cycle.json   ciclo do dia. O app NÃO toca esta animação: posiciona o
                     progresso no horário real (0 = meia-noite, 0.25 = nascer
                     do sol, 0.5 = meio-dia solar, 0.75 = pôr do sol).
  - energy_flow.json loop de 1 s com pulsos de energia nos cabos (gerando).
  - energy_import.json loop com a energia vindo da rede para a casa (sem geração).
  - clouds.json      loop lento com nuvens à deriva.

Todas usam a mesma prancheta de 400x340; o app sobrepõe as três e desenha
as "ondas" do sol em Compose usando SUN_ARC (espelhado em SceneGeometry.kt).

Uso: python3 tools/lottie/generate_scenes.py
"""
import json
import math
from pathlib import Path

W, H = 400, 340
# Céu extra no topo (título e barra de status ficam sobre ele): tudo que está
# "no chão" é desenhado nas coordenadas originais e deslocado DY para baixo.
DY = 80
OUT = Path(__file__).resolve().parents[2] / "app/src/main/res/raw"

# ---------------------------------------------------------------- utilidades


def rgb(hex_color, alpha=1.0):
    h = hex_color.lstrip("#")
    return [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)] + [alpha]


def static(value):
    return {"a": 0, "k": value}


def animated(frames):
    """frames: lista de (t, valor). Easing suave entre chaves."""
    keys = []
    for i, (t, value) in enumerate(frames):
        v = value if isinstance(value, list) else [value]
        key = {"t": t, "s": v}
        if i < len(frames) - 1:
            n = len(v)
            key["i"] = {"x": [0.5] * n, "y": [0.5] * n}
            key["o"] = {"x": [0.5] * n, "y": [0.5] * n}
        keys.append(key)
    return {"a": 1, "k": keys}


def transform(p=(0, 0), o=100, s=100):
    return {
        "o": o if isinstance(o, dict) else static(o),
        "r": static(0),
        "p": p if isinstance(p, dict) else static([p[0], p[1], 0]),
        "a": static([0, 0, 0]),
        "s": static([s, s, 100]),
    }


def group_tr():
    return {"ty": "tr", "p": static([0, 0]), "a": static([0, 0]), "s": static([100, 100]),
            "r": static(0), "o": static(100), "sk": static(0), "sa": static(0)}


def group(name, items):
    # Dentro do grupo a ordem é geometria → pintura (fill/stroke) → transformação.
    return {"ty": "gr", "nm": name, "it": items + [group_tr()]}


def rect(x, y, w, h, r=0):
    return {"ty": "rc", "d": 1, "s": static([w, h]), "p": static([x + w / 2, y + h / 2]), "r": static(r)}


def ellipse(cx, cy, w, h=None):
    return {"ty": "el", "d": 1, "s": static([w, h or w]), "p": static([cx, cy])}


def path(points, closed=True, tangents=None):
    n = len(points)
    zero = [[0, 0]] * n
    ins, outs = tangents if tangents else (zero, zero)
    return {"ty": "sh", "ks": static({"i": ins, "o": outs, "v": points, "c": closed})}


def curve(p0, c0, c1, p1):
    """Bezier cúbica aberta p0→p1 com pontos de controle absolutos."""
    return path([p0, p1], closed=False, tangents=(
        [[0, 0], [c1[0] - p1[0], c1[1] - p1[1]]],
        [[c0[0] - p0[0], c0[1] - p0[1]], [0, 0]],
    ))


def fill(color, opacity=100):
    c = color if isinstance(color, dict) else static(color)
    return {"ty": "fl", "c": c, "o": opacity if isinstance(opacity, dict) else static(opacity), "r": 1}


def stroke(color, width, opacity=100, dashes=None):
    s = {"ty": "st", "c": color if isinstance(color, dict) else static(color),
         "o": opacity if isinstance(opacity, dict) else static(opacity),
         "w": static(width), "lc": 2, "lj": 2, "ml": 4}
    if dashes:
        s["d"] = dashes
    return s


def linear_gradient(start, end, stops):
    """stops: dict anim/static com [pos, r, g, b, ...]."""
    return {"ty": "gf", "o": static(100), "r": 1, "t": 1, "s": static(start), "e": static(end),
            "g": {"p": 2, "k": stops}}


def radial_gradient(center, radius, stops):
    return {"ty": "gf", "o": static(100), "r": 1, "t": 2, "s": static(center),
            "e": static([center[0] + radius, center[1]]), "h": static(0), "a": static(0),
            "g": {"p": 2, "k": stops}}


def layer(index, name, shapes, op, ks=None):
    # Shapes declarados de trás para frente; no Lottie o primeiro item fica por cima.
    return {"ddd": 0, "ind": index, "ty": 4, "nm": name, "sr": 1, "ks": ks or transform(),
            "ao": 0, "shapes": list(reversed(shapes)), "ip": 0, "op": op, "st": 0, "bm": 0}


def composition(name, op, layers, fr=30):
    # Lottie desenha a primeira camada por cima: invertemos para declarar de trás para frente.
    ordered = list(reversed(layers))
    for i, lyr in enumerate(ordered, start=1):
        lyr["ind"] = i
    return {"v": "5.7.4", "fr": fr, "ip": 0, "op": op, "w": W, "h": H, "nm": name,
            "ddd": 0, "assets": [], "layers": ordered}


# ------------------------------------------------------------- ciclo do dia

DAY = 1200  # frames do ciclo: progresso p ↔ frame p*DAY


def f(p):
    return round(p * DAY, 2)


def by_phase(phases):
    """phases: lista (p, valor) → keyframes no tempo do ciclo."""
    return animated([(f(p), v) for p, v in phases])


def color_phases(spec):
    return by_phase([(p, rgb(c)) for p, c in spec])


SKY = [  # (p, topo, horizonte)
    (0.00, "#070B1E", "#141B3A"),
    (0.17, "#0D1433", "#2A2552"),
    (0.22, "#26305F", "#B76A7A"),
    (0.26, "#4A69A8", "#FFB27A"),
    (0.32, "#5A97DC", "#CFE6F7"),
    (0.50, "#3E8AE6", "#B3DCFB"),
    (0.68, "#4C8AD6", "#D6E6F2"),
    (0.74, "#4A4F8F", "#FF9A62"),
    (0.79, "#262A5C", "#8A4A72"),
    (0.84, "#0D1433", "#2A2552"),
    (1.00, "#070B1E", "#141B3A"),
]


def sky_layer():
    stops = by_phase([(p, [0, *rgb(top)[:3], 1, *rgb(bottom)[:3]]) for p, top, bottom in SKY])
    return layer(0, "sky", [group("sky", [rect(0, 0, W, H), linear_gradient([W / 2, 0], [W / 2, 200 + DY], stops)])], DAY)


def stars_layer():
    import random
    rnd = random.Random(7)
    items = []
    for _ in range(46):
        x, y = rnd.uniform(5, W - 5), rnd.uniform(5, 150 + DY)
        size = rnd.choice([1.2, 1.6, 2.0, 2.6])
        items.append(group("star", [ellipse(x, y, size), fill(rgb("#FFFFFF"), rnd.randint(55, 100))]))
    opacity = by_phase([(0.0, 100), (0.17, 90), (0.24, 0), (0.76, 0), (0.83, 90), (1.0, 100)])
    return layer(0, "stars", items, DAY, transform(o=opacity))


# Arco do sol (mesma fórmula em SceneGeometry.kt): nasce à esquerda e se põe à direita.
def sun_arc(p):
    u = (p - 0.25) / 0.5
    return [30 + 340 * u, DY + 228 - 188 * math.sin(math.pi * min(max(u, 0), 1))]


def moon_arc(p):
    # Noite de 0.75 → 1.25 (≡ 0.25 do dia seguinte): sobe à esquerda, desce à direita.
    q = p + 1 if p < 0.5 else p
    u = (q - 0.77) / 0.46
    return [40 + 320 * u, DY + 210 - 150 * math.sin(math.pi * min(max(u, 0), 1))]


def sun_layer():
    steps = [0.22 + i * 0.0125 for i in range(int((0.78 - 0.22) / 0.0125) + 1)]
    position = by_phase([(p, [*sun_arc(p), 0]) for p in steps])
    opacity = by_phase([(0.22, 0), (0.25, 100), (0.75, 100), (0.78, 0)])
    glow = by_phase([(p, [0, *rgb(c)[:3], 1, *rgb(c)[:3], 0, 0.55, 1, 0]) for p, c in
                     [(0.24, "#FF8A3D"), (0.32, "#FFD36B"), (0.5, "#FFF1B8"), (0.68, "#FFD36B"), (0.76, "#FF7A3D")]])
    core = color_phases([(0.24, "#FF9A4D"), (0.32, "#FFE08A"), (0.5, "#FFF6D6"), (0.68, "#FFE08A"), (0.76, "#FF8A4D")])
    shapes = [
        group("halo", [ellipse(0, 0, 120), radial_gradient([0, 0], 60, glow)]),
        group("core", [ellipse(0, 0, 34), fill(core)]),
    ]
    return layer(0, "sun", shapes, DAY, transform(p=position, o=opacity))


def moon_layer():
    steps = [0.75 + i * 0.0125 for i in range(41)]  # 0.75 → 1.25
    position = by_phase(sorted([((p if p < 1 else p - 1), [*moon_arc(p if p < 1 else p - 1), 0]) for p in steps]))
    opacity = by_phase([(0.0, 100), (0.2, 100), (0.24, 0), (0.76, 0), (0.8, 100), (1.0, 100)])
    shapes = [
        group("glow", [ellipse(0, 0, 70), radial_gradient([0, 0], 35, static([0, 0.85, 0.9, 1, 1, 0.85, 0.9, 1, 0, 0.35, 1, 0]))]),
        group("disc", [ellipse(0, 0, 22), fill(rgb("#F4F1E6"))]),
        group("crater", [ellipse(-4, -3, 5), fill(rgb("#DCD7C6"))]),
        group("crater", [ellipse(4, 4, 3.5), fill(rgb("#DCD7C6"))]),
    ]
    return layer(0, "moon", shapes, DAY, transform(p=position, o=opacity))


def lit(day, night, dusk=None):
    """Cor que acompanha a luz: noite → amanhecer → dia → entardecer → noite."""
    dusk = dusk or day
    return color_phases([(0.0, night), (0.2, night), (0.27, dusk), (0.33, day), (0.67, day),
                         (0.73, dusk), (0.8, night), (1.0, night)])


def landscape_layer():
    far_hills = path([[0, 200], [0, 186], [60, 170], [130, 181], [210, 164], [300, 177], [400, 163], [400, 200]])
    lake = rect(0, 190, W, 16)
    mid_hills = path([[0, 208], [0, 197], [80, 186], [170, 199], [250, 190], [330, 199], [400, 189], [400, 208]])
    front = path([[0, 260], [0, 208], [120, 200], [260, 206], [400, 198], [400, 260]])
    shine = [rect(x, y, w, 1.4, 0.7) for x, y, w in [(40, 196, 34), (150, 199, 22), (300, 195, 40), (360, 201, 18)]]
    shapes = [
        group("farHills", [far_hills, fill(lit("#8FC2A2", "#15262C", "#B09C82"))]),
        group("lake", [lake, fill(lit("#7EC6EA", "#1A2B45", "#E9AE8C"))]),
        group("lakeShine", shine + [fill(rgb("#FFFFFF"), by_phase([(0.0, 25), (0.25, 40), (0.5, 70), (0.75, 40), (1.0, 25)]))]),
        group("midHills", [mid_hills, fill(lit("#9CCB86", "#1A2B26", "#B39A6E"))]),
        group("ground", [front, fill(lit("#7DBB6A", "#14261C", "#8C9A5E"))]),
    ]
    return layer(0, "landscape", shapes, DAY, transform(p=(0, DY)))


def bush(x, y, scale=1.0, tone=0):
    s = scale
    day = ["#4E9A57", "#5DAA5E", "#3F8A4B"][tone % 3]
    puffs = [(0, 0, 22, 14), (-10, 2, 16, 11), (10, 2, 17, 12), (2, -5, 15, 12)]
    return [group("bush", [ellipse(x + dx * s, y + dy * s, w * s, h * s) for dx, dy, w, h in puffs] +
                  [fill(lit(day, "#122417", "#6E8A4A"))])]


def foliage():
    """Folhagem escura em primeiro plano nos cantos (o app esmaece a base da cena)."""
    leaves = []
    for cx, cy, w, h in [(-6, 252, 70, 36), (22, 258, 60, 30), (392, 250, 66, 36), (366, 258, 58, 28)]:
        leaves.append(ellipse(cx, cy, w, h))
    return [group("foliage", leaves + [fill(lit("#3E8247", "#0E1E13", "#55703C"))])]


def tree(x, y, scale=1.0):
    s = scale
    return [
        group("trunk", [rect(x - 2.5 * s, y - 14 * s, 5 * s, 16 * s, 1), fill(lit("#7A5536", "#2A1E18"))]),
        group("crown", [ellipse(x, y - 26 * s, 30 * s, 28 * s), fill(lit("#4F9A55", "#14281C", "#6E8A4A"))]),
        group("crown2", [ellipse(x - 9 * s, y - 18 * s, 20 * s, 18 * s), fill(lit("#5CAA60", "#183020", "#7E9A55"))]),
    ]


# Geometria da casa (espelhada em SceneGeometry.kt para as ondas do sol).
PANEL = [[198, 110], [270, 110], [298, 150], [226, 150]]  # face do telhado com placas
INVERTER = (281, 168, 13, 18)


def house_layer():
    window_light = color_phases([(0.0, "#FFD27A"), (0.22, "#FFD27A"), (0.27, "#9CC4E4"), (0.73, "#9CC4E4"),
                                 (0.78, "#FFD27A"), (1.0, "#FFD27A")])
    shapes = [
        # Poste e fios da rede elétrica.
        group("wires", [curve([352, 132], [375, 140], [390, 140], [410, 134]), stroke(lit("#3D3D45", "#7A7A88"), 1.2)]),
        group("pole", [rect(352, 118, 6, 98, 1), fill(lit("#6B5A4E", "#2B2523"))]),
        group("crossarm", [rect(340, 128, 30, 4, 1), fill(lit("#6B5A4E", "#2B2523"))]),
        group("insulator", [ellipse(343, 126, 4), ellipse(367, 126, 4), fill(lit("#D9D9DE", "#55555E"))]),
        # Cabo inversor → poste (o fluxo animado percorre o mesmo traço).
        group("gridCable", [curve([294, 178], [318, 196], [336, 160], [346, 130]), stroke(lit("#2F2F36", "#55555E"), 1.6)]),
        # Parede lateral, frente e empena.
        group("sideWall", [path([[230, 150], [300, 150], [300, 215], [230, 215]]), fill(lit("#E9D8C2", "#3A3345", "#E8C2A0"))]),
        group("frontWall", [rect(160, 150, 70, 65), fill(lit("#F6EBDD", "#433B50", "#F3D2B4"))]),
        group("gable", [path([[154, 153], [196, 108], [236, 153]]), fill(lit("#C9603E", "#4A2A2E", "#D4693E"))]),
        group("roofEdge", [path([[196, 108], [270, 108], [306, 153], [236, 153]]), fill(lit("#B5532F", "#3D2427", "#C25B33"))]),
        # Placas solares na face do telhado (3x2 módulos).
        group("panel", [path(PANEL), fill(lit("#1D3E73", "#121C33", "#2A3F6E"))]),
        group("panelGrid", [
            curve([222, 110], [222, 110], [250, 150], [250, 150]),
            curve([246, 110], [246, 110], [274, 150], [274, 150]),
            curve([212, 130], [212, 130], [284, 130], [284, 130]),
            stroke(lit("#7FA6E0", "#2D3A5A"), 1.1),
        ]),
        group("panelShine", [path([[204, 112], [222, 112], [232, 126], [214, 126]]), fill(rgb("#FFFFFF"), by_phase(
            [(0.0, 8), (0.25, 10), (0.5, 28), (0.75, 10), (1.0, 8)]))]),
        # Porta, janela (acende à noite) e inversor.
        group("door", [rect(200, 176, 18, 39, 2), fill(lit("#8A5A3C", "#2E2026"))]),
        group("knob", [ellipse(214, 196, 2.5), fill(lit("#E2B862", "#6A5A40"))]),
        group("window", [rect(170, 166, 22, 20, 2), fill(window_light)]),
        group("windowFrame", [rect(170, 166, 22, 20, 2), stroke(lit("#FFFFFF", "#6A6070"), 1.5)]),
        group("sideWindow", [rect(246, 168, 22, 18, 2), fill(window_light)]),
        group("inverter", [rect(*INVERTER, 2), fill(lit("#F2F2F5", "#5A5A66"))]),
        group("inverterLed", [ellipse(INVERTER[0] + 6.5, INVERTER[1] + 5, 3), fill(rgb("#2FBF71"))]),
        group("panelCable", [curve([288, 146], [289, 154], [289, 160], [288, 168]), stroke(lit("#2F2F36", "#55555E"), 1.6)]),
    ]
    front = (bush(168, 214, 1.0, 1) + bush(296, 214, 0.9, 0) + bush(140, 212, 0.8, 2) +
             bush(60, 214, 1.1, 0) + bush(340, 218, 0.9, 1) + foliage())
    shapes = tree(112, 206, 1.15) + tree(80, 210, 0.8) + tree(380, 214, 0.7) + tree(26, 204, 0.75) + shapes + front
    return layer(0, "house", shapes, DAY, transform(p=(0, DY)))


def sky_cycle():
    return composition("sky_cycle", DAY, [
        sky_layer(), stars_layer(), moon_layer(), sun_layer(), landscape_layer(), house_layer(),
    ])


# --------------------------------------------------------- fluxo de energia

FLOW_FRAMES = 30  # 1 s a 30 fps; o app ajusta a velocidade pela potência


def flow_line(name, points_curve, color="#FFC23D", core="#FFF4C2"):
    offset = animated([(0, 0), (FLOW_FRAMES, -36)])
    # Linear: o loop precisa ser contínuo.
    for k in offset["k"][:-1]:
        k["i"] = {"x": [1], "y": [1]}
        k["o"] = {"x": [0], "y": [0]}
    dashes = [{"n": "d", "nm": "dash", "v": static(8)}, {"n": "g", "nm": "gap", "v": static(28)},
              {"n": "o", "nm": "offset", "v": offset}]
    return layer(0, name, [
        group("glow", [points_curve, stroke(rgb(color), 6, 35, dashes)]),
        group("core", [points_curve, stroke(rgb(core), 2.2, 100, dashes)]),
    ], FLOW_FRAMES, transform(p=(0, DY)))


def energy_flow():
    return composition("energy_flow", FLOW_FRAMES, [
        flow_line("flow_panel", curve([288, 146], [289, 154], [289, 160], [288, 168])),
        flow_line("flow_house", curve([282, 182], [266, 182], [248, 184], [232, 184])),
        flow_line("flow_grid", curve([294, 178], [318, 196], [336, 160], [346, 130])),
    ])


def energy_import():
    """Sem geração (noite): a casa consome da rede, então o fluxo vem do poste."""
    blue, core = "#5AA8FF", "#DDEEFF"
    return composition("energy_import", FLOW_FRAMES, [
        flow_line("import_grid", curve([346, 130], [336, 160], [318, 196], [294, 178]), blue, core),
        flow_line("import_house", curve([282, 182], [266, 182], [248, 184], [232, 184]), blue, core),
    ])


# ------------------------------------------------------------------ nuvens

CLOUD_FRAMES = 30 * 40  # 40 s


def cloud(cx, cy, scale):
    s = scale
    puffs = [(0, 0, 46, 26), (-18, 4, 30, 20), (18, 3, 34, 22), (6, -9, 30, 24)]
    return [group("puff", [ellipse(cx + dx * s, cy + dy * s, w * s, h * s), fill(rgb("#FFFFFF"))]) for dx, dy, w, h in puffs]


def clouds():
    def drifting(name, y, scale, x0, opacity):
        # Começa em x0, sai pela direita, reaparece pela esquerda e volta a x0:
        # velocidade constante e loop sem emenda.
        left, right = -70, W + 70
        span = right - left
        t_exit = round((right - x0) / span * CLOUD_FRAMES, 2)
        position = animated([(0, [x0, 0, 0]), (t_exit, [right, 0, 0]), (t_exit + 0.01, [left, 0, 0]),
                             (CLOUD_FRAMES, [x0, 0, 0])])
        for k in position["k"][:-1]:
            k["i"] = {"x": [1], "y": [1]}
            k["o"] = {"x": [0], "y": [0]}
        return layer(0, name, cloud(0, y, scale), CLOUD_FRAMES, transform(p=position, o=opacity))

    return composition("clouds", CLOUD_FRAMES, [
        drifting("cloudA", 70, 1.6, 60, 95),
        drifting("cloudB", 120, 1.1, 330, 85),
        drifting("cloudC", 36, 1.25, 230, 75),
        drifting("cloudD", 150, 0.8, 150, 60),
    ])


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in [("sky_cycle", sky_cycle()), ("energy_flow", energy_flow()),
                       ("energy_import", energy_import()), ("clouds", clouds())]:
        target = OUT / f"{name}.json"
        target.write_text(json.dumps(data, separators=(",", ":")))
        print(f"{target.relative_to(OUT.parents[4])}: {target.stat().st_size // 1024} KB")
