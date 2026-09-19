import os
import math
from PIL import Image, ImageDraw, ImageFont
from schem_builder import SpongeSchematic
from prefab_library import build_deepslate_corridor_segment

BLOCK_COLORS = {
    "minecraft:air": None,
    "minecraft:polished_deepslate": "#334155",        # Slate 700
    "minecraft:deepslate_bricks": "#1e293b",          # Slate 800
    "minecraft:cracked_deepslate_bricks": "#0f172a",  # Slate 900
    "minecraft:deepslate_tiles": "#475569",           # Slate 600
    "minecraft:chiseled_deepslate": "#64748b",        # Slate 500
    "minecraft:deepslate_tile_wall": "#3b82f6",       # Accent Blue
    "minecraft:dark_oak_planks": "#451a03",           # Dark Wood Warm
    "minecraft:glass": "#38bdf8",                     # Cyan Glass
    "minecraft:lantern": "#fbbf24",                   # Glowing Amber Lantern
    "minecraft:chain": "#94a3b8",                     # Steel Gray
}

def get_block_color(b_name: str) -> str:
    for k, col in BLOCK_COLORS.items():
        if k in b_name:
            return col
    if "stairs" in b_name:
        return "#475569"
    return "#52525b"

def hex_to_rgb(hex_str):
    hex_str = hex_str.lstrip('#')
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))

def shade_color(hex_str, factor):
    r, g, b = hex_to_rgb(hex_str)
    return (int(r * factor), int(g * factor), int(b * factor))

def render_schematic_cad_sheet(schem: SpongeSchematic, title: str, output_path: str):
    W, H = 2400, 1400
    img = Image.new("RGB", (W, H), "#09090b") # Zinc 950
    draw = ImageDraw.Draw(img)

    try:
        font_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 36)
        font_header = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 22)
        font_body = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 16)
        font_mono = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 14)
    except Exception:
        font_title = font_header = font_body = font_mono = ImageFont.load_default()

    # Subtle Grid Background
    for x in range(0, W, 40):
        draw.line([(x, 0), (x, H)], fill="#18181b", width=1)
    for y in range(0, H, 40):
        draw.line([(0, y), (W, y)], fill="#18181b", width=1)

    # Header Bar
    draw.rectangle([(60, 40), (W - 60, 110)], fill="#111827", outline="#27272a", width=1)
    draw.text((90, 52), f"MODULAR PREFAB BLUEPRINT: {title.upper()}", fill="#f43f5e", font=font_title)
    draw.text((90, 88), f"DIMENSIONS: {schem.width}W (X) x {schem.height}H (Y) x {schem.length}L (Z) | NATIVE BARITONE SPONGE V2 SCHEMATIC", fill="#71717a", font=font_body)
    draw.text((W - 350, 68), "SPEC: EFFECTOR MCP v1.2", fill="#10b981", font=font_header)

    # --- PANEL 1: 3D ISOMETRIC AXONOMETRIC CUTAWAY (X: 100..1000, Y: 160..860) ---
    draw.text((100, 140), "VIEW A: 3D AXONOMETRIC ISOMETRIC CUTAWAY", fill="#e4e4e7", font=font_header)
    draw.rectangle([(100, 180), (1020, 860)], fill="#18181b", outline="#27272a", width=1)

    # Isometric projection constants
    iso_origin_x = 560
    iso_origin_y = 660
    u_x, u_y = 38, -22 # Vector for X
    v_x, v_y = -34, -20 # Vector for Z
    w_y = -34           # Vector for Y (up)

    def to_iso(x, y, z):
        px = iso_origin_x + x * u_x + z * v_x
        py = iso_origin_y + x * u_y + z * v_y + y * w_y
        return px, py

    # Draw voxels back-to-front: z from max down to 0, y from 0 up to max, x from 0 up to max
    for z in range(schem.length - 1, -1, -1):
        for y in range(schem.height):
            for x in range(schem.width):
                b = schem.grid[y][z][x]
                if b == "minecraft:air":
                    continue
                col_hex = get_block_color(b)
                if not col_hex:
                    continue

                top_col = shade_color(col_hex, 1.15)
                left_col = shade_color(col_hex, 0.75)
                right_col = shade_color(col_hex, 0.90)

                # 8 vertices of cube
                p000 = to_iso(x, y, z)
                p100 = to_iso(x + 1, y, z)
                p101 = to_iso(x + 1, y, z + 1)
                p001 = to_iso(x, y, z + 1)

                p010 = to_iso(x, y + 1, z)
                p110 = to_iso(x + 1, y + 1, z)
                p111 = to_iso(x + 1, y + 1, z + 1)
                p011 = to_iso(x, y + 1, z + 1)

                # Draw Top face
                draw.polygon([p010, p110, p111, p011], fill=top_col, outline="#09090b")
                # Draw Left face (facing +X)
                draw.polygon([p100, p110, p111, p101], fill=left_col, outline="#09090b")
                # Draw Right face (facing +Z)
                draw.polygon([p001, p011, p111, p101], fill=right_col, outline="#09090b")

    # --- PANEL 2: CROSS-SECTION ELEVATION (X: 1080..1680, Y: 160..860) ---
    draw.text((1080, 140), "VIEW B: ARCHITECTURAL SECTION PROFILE (Y:0..5)", fill="#e4e4e7", font=font_header)
    draw.rectangle([(1080, 180), (1680, 860)], fill="#18181b", outline="#27272a", width=1)

    cell_cw = 68
    cell_ch = 68
    c_start_x = 1110
    c_start_y = 740

    sec_z = 0
    for y in range(schem.height):
        for x in range(schem.width):
            bx = c_start_x + x * cell_cw
            by = c_start_y - y * cell_ch
            b = schem.grid[y][sec_z][x]
            col = get_block_color(b)
            if col:
                draw.rectangle([(bx, by), (bx + cell_cw - 4, by + cell_ch - 4)], fill=col, outline="#3f3f46", width=1)
                if "stairs" in b:
                    draw.text((bx + 14, by + 22), "STAIR", fill="#ffffff", font=font_mono)
                elif "glass" in b:
                    draw.text((bx + 14, by + 22), "GLASS", fill="#ffffff", font=font_mono)
                elif "lantern" in b or "chain" in b:
                    draw.text((bx + 8, by + 22), "LIGHT", fill="#fbbf24", font=font_mono)
                else:
                    short_name = b.replace("minecraft:", "").split("[")[0][:7].upper()
                    draw.text((bx + 8, by + 22), short_name, fill="#ffffff", font=font_mono)
            else:
                draw.rectangle([(bx, by), (bx + cell_cw - 4, by + cell_ch - 4)], fill="#18181b", outline="#27272a", width=1)

    # Elevation axis labels
    for y in range(schem.height):
        by = c_start_y - y * cell_ch + 22
        draw.text((1086, by), f"Y{y}", fill="#a1a1aa", font=font_mono)
    for x in range(schem.width):
        bx = c_start_x + x * cell_cw + 24
        draw.text((bx, c_start_y + 74), f"X{x}", fill="#a1a1aa", font=font_mono)

    # --- PANEL 3: BILL OF MATERIALS & TELEMETRY (X: 1740..2340, Y: 160..1320) ---
    draw.text((1740, 140), "VIEW C: BILL OF MATERIALS & SPEC", fill="#e4e4e7", font=font_header)
    draw.rectangle([(1740, 180), (2340, 1320)], fill="#18181b", outline="#27272a", width=1)

    draw.text((1765, 210), "MATERIAL REQUIREMENTS:", fill="#f43f5e", font=font_header)
    reqs = schem.get_material_requirements()
    cur_y = 260
    total_blocks = 0
    for mat, count in reqs.items():
        total_blocks += count
        clean_name = mat.replace("minecraft:", "")
        draw.text((1765, cur_y), f"- {clean_name}", fill="#e4e4e7", font=font_body)
        draw.text((2260, cur_y), f"{count:3d}", fill="#38bdf8", font=font_header)
        cur_y += 34
        if cur_y > 880:
            break

    draw.line([(1765, cur_y + 10), (2300, cur_y + 10)], fill="#27272a", width=2)
    draw.text((1765, cur_y + 25), "TOTAL BLOCKS NEEDED:", fill="#a1a1aa", font=font_header)
    draw.text((2240, cur_y + 25), f"{total_blocks:4d}", fill="#10b981", font=font_title)

    # Highlights Box
    cur_y += 90
    draw.rectangle([(1760, cur_y), (2310, cur_y + 280)], fill="#111827", outline="#3f3f46", width=1)
    draw.text((1780, cur_y + 20), "ARCHITECTURAL HIGHLIGHTS:", fill="#fbbf24", font=font_header)
    notes = [
        "1. True 3D Depth: Protruding columns & recessed wall bays.",
        "2. Gothic Vault Arches: Inverted deepslate stairs.",
        "3. Overhead Conduit: Embedded Y:58 glass duct.",
        "4. Ambient Lighting: Suspended chain lanterns.",
        "5. 100% Deterministic: Native Baritone Sponge .schem."
    ]
    for i, n in enumerate(notes):
        draw.text((1780, cur_y + 60 + i * 40), n, fill="#d4d4d8", font=font_body)

    # Bottom Area: Module Socket Topology (X: 100..1680, Y: 920..1320)
    draw.text((100, 890), "MODULE TOPOLOGY & SOCKET SPECIFICATION", fill="#e4e4e7", font=font_header)
    draw.rectangle([(100, 930), (1680, 1320)], fill="#18181b", outline="#27272a", width=1)

    draw.text((130, 960), "SOCKET CONTRACT (PORTAL ALIGNMENT):", fill="#38bdf8", font=font_header)
    draw.text((130, 1000), "- NORTH SOCKET (Z=0): Open Corridor Portal 4x3 (X:2..5, Y:1..3), Conduit Port (X:3..4, Y:4)", fill="#e4e4e7", font=font_body)
    draw.text((130, 1035), "- SOUTH SOCKET (Z=4): Open Corridor Portal 4x3 (X:2..5, Y:1..3), Conduit Port (X:3..4, Y:4)", fill="#e4e4e7", font=font_body)
    draw.text((130, 1070), "- EAST SOCKET  (X=7): Solid Recessed Foundation with Fluted Columns", fill="#a1a1aa", font=font_body)
    draw.text((130, 1105), "- WEST SOCKET  (X=0): Solid Recessed Foundation with Fluted Columns", fill="#a1a1aa", font=font_body)

    draw.text((130, 1160), "CONSTRUCTION DISCIPLINE (BARITONE EXECUTION PROTOCOL):", fill="#10b981", font=font_header)
    draw.text((130, 1200), "1. Staging: Baritone automatically verifies inventory for 182 required blocks before placing.", fill="#d4d4d8", font=font_body)
    draw.text((130, 1235), "2. Layer-by-Layer: Places Y=0 floor first, building up to Y=5 roof to eliminate falling block glitches.", fill="#d4d4d8", font=font_body)
    draw.text((130, 1270), "3. Re-run Safety: buildIgnoreExisting allows non-destructive detailing and zero structural griefing.", fill="#d4d4d8", font=font_body)

    # Footer
    draw.text((100, 1350), "SUBTERRANEAN MEGA FACILITY ARCHITECTURAL SUITE // EFFECTOR MCP + BARITONE NATIVE PIPELINE", fill="#52525b", font=font_mono)

    img.save(output_path, "PNG")
    print(f"Generated Enhanced 3D CAD Blueprint Sheet at {output_path}")

if __name__ == "__main__":
    c = build_deepslate_corridor_segment(length=5)
    out = "C:/Users/Administrator/schematic_pipeline/corridor_module_cad_blueprint_3d.png"
    render_schematic_cad_sheet(c, "Industrial Deepslate Corridor Segment (5Z)", out)
