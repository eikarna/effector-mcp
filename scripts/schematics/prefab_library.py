from schem_builder import SpongeSchematic

def build_corridor_segment(length: int = 5, style: str = "deepslate") -> SpongeSchematic:
    """
    Modular Industrial Bunker Corridor Segment.
    Dimensions: 8 (X) x 6 (Y) x length (Z)
    Styles: 'deepslate' (Deepslate/Dark Oak) or 'stone_diorite' (Stone Bricks/Diorite/Oak)
    """
    width = 8
    height = 6
    s = SpongeSchematic(width, height, length)

    if style == "stone_diorite":
        mat_border = "minecraft:polished_diorite"
        mat_floor_side = "minecraft:stone_bricks"
        mat_walkway = "minecraft:oak_planks"
        mat_pillar_base = "minecraft:diorite"
        mat_pillar_mid = "minecraft:polished_diorite"
        mat_pillar_wall = "minecraft:cobblestone_wall"
        mat_wall_panel = "minecraft:stone_bricks"
        mat_wall_accent = "minecraft:cracked_stone_bricks"
        mat_stairs_east = "minecraft:stone_brick_stairs[facing=east,half=top]"
        mat_stairs_west = "minecraft:stone_brick_stairs[facing=west,half=top]"
        mat_roof = "minecraft:stone_bricks"
        mat_casing = "minecraft:polished_diorite"
    else: # default deepslate
        mat_border = "minecraft:polished_deepslate"
        mat_floor_side = "minecraft:deepslate_tiles"
        mat_walkway = "minecraft:dark_oak_planks"
        mat_pillar_base = "minecraft:chiseled_deepslate"
        mat_pillar_mid = "minecraft:polished_deepslate"
        mat_pillar_wall = "minecraft:deepslate_tile_wall"
        mat_wall_panel = "minecraft:deepslate_bricks"
        mat_wall_accent = "minecraft:cracked_deepslate_bricks"
        mat_stairs_east = "minecraft:polished_deepslate_stairs[facing=east,half=top]"
        mat_stairs_west = "minecraft:polished_deepslate_stairs[facing=west,half=top]"
        mat_roof = "minecraft:deepslate_bricks"
        mat_casing = "minecraft:polished_deepslate"

    for z in range(length):
        is_pillar_z = (z % 4 == 0)

        # --- Y=0: Floor Level ---
        s.set_block(0, 0, z, mat_border)
        s.set_block(1, 0, z, mat_border)
        s.set_block(6, 0, z, mat_border)
        s.set_block(7, 0, z, mat_border)
        s.set_block(2, 0, z, mat_floor_side)
        s.set_block(3, 0, z, mat_walkway)
        s.set_block(4, 0, z, mat_walkway)
        s.set_block(5, 0, z, mat_floor_side)

        # --- Y=1 & Y=2: Wall Elevation ---
        if is_pillar_z:
            s.set_block(0, 1, z, mat_border)
            s.set_block(0, 2, z, mat_border)
            s.set_block(1, 1, z, mat_pillar_base)
            s.set_block(1, 2, z, mat_pillar_wall)

            s.set_block(7, 1, z, mat_border)
            s.set_block(7, 2, z, mat_border)
            s.set_block(6, 1, z, mat_pillar_base)
            s.set_block(6, 2, z, mat_pillar_wall)
        else:
            s.set_block(0, 1, z, mat_wall_panel)
            s.set_block(0, 2, z, mat_wall_accent)
            s.set_block(1, 1, z, "minecraft:air")
            s.set_block(1, 2, z, "minecraft:air")

            s.set_block(7, 1, z, mat_wall_panel)
            s.set_block(7, 2, z, mat_wall_accent)
            s.set_block(6, 1, z, "minecraft:air")
            s.set_block(6, 2, z, "minecraft:air")

        for x in range(2, 6):
            s.set_block(x, 1, z, "minecraft:air")
            s.set_block(x, 2, z, "minecraft:air")

        # --- Y=3: Arch Corbels & Lintel ---
        s.set_block(0, 3, z, mat_border)
        s.set_block(7, 3, z, mat_border)
        s.set_block(1, 3, z, mat_stairs_east)
        s.set_block(2, 3, z, mat_stairs_east)
        s.set_block(5, 3, z, mat_stairs_west)
        s.set_block(6, 3, z, mat_stairs_west)
        s.set_block(3, 3, z, "minecraft:air")
        s.set_block(4, 3, z, "minecraft:air")

        if is_pillar_z:
            s.set_block(3, 3, z, "minecraft:chain")
            s.set_block(3, 2, z, "minecraft:lantern[hanging=true]")

        # --- Y=4: Overhead Conduit Level ---
        s.set_block(0, 4, z, mat_floor_side)
        s.set_block(1, 4, z, mat_floor_side)
        s.set_block(2, 4, z, mat_casing)
        s.set_block(3, 4, z, "minecraft:glass")
        s.set_block(4, 4, z, "minecraft:glass")
        s.set_block(5, 4, z, mat_casing)
        s.set_block(6, 4, z, mat_floor_side)
        s.set_block(7, 4, z, mat_floor_side)

        # --- Y=5: Solid Roof Cap ---
        for x in range(width):
            s.set_block(x, 5, z, mat_roof)

    return s

def build_deepslate_corridor_segment(length: int = 5) -> SpongeSchematic:
    return build_corridor_segment(length=length, style="deepslate")

def build_stone_diorite_corridor_segment(length: int = 5) -> SpongeSchematic:
    return build_corridor_segment(length=length, style="stone_diorite")

if __name__ == "__main__":
    c_deepslate = build_deepslate_corridor_segment(length=5)
    c_deepslate.export_to_baritone("module_corridor_deepslate_5z.schem")

    c_stone = build_stone_diorite_corridor_segment(length=5)
    out_stone = c_stone.export_to_baritone("module_corridor_stone_diorite_5z.schem")
    print(f"Exported stone_diorite module: {out_stone}")
    print("Material requirements for stone_diorite:")
    for m, cnt in c_stone.get_material_requirements().items():
        print(f"  {m}: {cnt}")
