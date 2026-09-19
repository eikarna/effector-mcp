from schem_builder import SpongeSchematic

def build_deepslate_corridor_segment(length: int = 5) -> SpongeSchematic:
    """
    Modular Gothic-Industrial Bunker Corridor Segment.
    Dimensions: 8 (X) x 6 (Y) x length (Z)
    Y:0 = Floor (Y:54)
    Y:1..2 = Walking & Wall Depth (Y:55..56)
    Y:3 = Arch Corbels & Lintel (Y:57)
    Y:4 = Overhead Duct Casing & Ceiling (Y:58)
    Y:5 = Solid Bunker Roof (Y:59)
    """
    width = 8
    height = 6
    s = SpongeSchematic(width, height, length)

    for z in range(length):
        is_pillar_z = (z % 4 == 0) # Pillars every 4 blocks for rhythm & depth

        # --- Y=0: Floor Level ---
        # Outer border: Polished Deepslate
        s.set_block(0, 0, z, "minecraft:polished_deepslate")
        s.set_block(1, 0, z, "minecraft:polished_deepslate")
        s.set_block(6, 0, z, "minecraft:polished_deepslate")
        s.set_block(7, 0, z, "minecraft:polished_deepslate")
        # Center walking strip: Deepslate Tiles / Dark Oak Planks
        s.set_block(2, 0, z, "minecraft:deepslate_tiles")
        s.set_block(3, 0, z, "minecraft:dark_oak_planks")
        s.set_block(4, 0, z, "minecraft:dark_oak_planks")
        s.set_block(5, 0, z, "minecraft:deepslate_tiles")

        # --- Y=1 & Y=2: Wall Elevation ---
        if is_pillar_z:
            # Protruding Structural Columns (Depth +1 toward center)
            s.set_block(0, 1, z, "minecraft:polished_deepslate")
            s.set_block(0, 2, z, "minecraft:polished_deepslate")
            s.set_block(1, 1, z, "minecraft:chiseled_deepslate")
            s.set_block(1, 2, z, "minecraft:deepslate_tile_wall")

            s.set_block(7, 1, z, "minecraft:polished_deepslate")
            s.set_block(7, 2, z, "minecraft:polished_deepslate")
            s.set_block(6, 1, z, "minecraft:chiseled_deepslate")
            s.set_block(6, 2, z, "minecraft:deepslate_tile_wall")
        else:
            # Recessed Wall Panels (1 block back, creating true 3D relief)
            s.set_block(0, 1, z, "minecraft:deepslate_bricks")
            s.set_block(0, 2, z, "minecraft:cracked_deepslate_bricks")
            # Air alcove at x=1 and x=6 for depth
            s.set_block(1, 1, z, "minecraft:air")
            s.set_block(1, 2, z, "minecraft:air")

            s.set_block(7, 1, z, "minecraft:deepslate_bricks")
            s.set_block(7, 2, z, "minecraft:cracked_deepslate_bricks")
            s.set_block(6, 1, z, "minecraft:air")
            s.set_block(6, 2, z, "minecraft:air")

        # Walking space in center (x=2..5) is Air
        for x in range(2, 6):
            s.set_block(x, 1, z, "minecraft:air")
            s.set_block(x, 2, z, "minecraft:air")

        # --- Y=3: Arch Corbels & Lintel ---
        s.set_block(0, 3, z, "minecraft:polished_deepslate")
        s.set_block(7, 3, z, "minecraft:polished_deepslate")
        # Inverted Deepslate Stairs creating Gothic Vaulted Arches
        s.set_block(1, 3, z, "minecraft:polished_deepslate_stairs[facing=east,half=top]")
        s.set_block(2, 3, z, "minecraft:polished_deepslate_stairs[facing=east,half=top]")
        s.set_block(5, 3, z, "minecraft:polished_deepslate_stairs[facing=west,half=top]")
        s.set_block(6, 3, z, "minecraft:polished_deepslate_stairs[facing=west,half=top]")
        # Center head clearance
        s.set_block(3, 3, z, "minecraft:air")
        s.set_block(4, 3, z, "minecraft:air")

        # Lantern on pillars
        if is_pillar_z:
            s.set_block(3, 3, z, "minecraft:chain")
            s.set_block(3, 2, z, "minecraft:lantern[hanging=true]")

        # --- Y=4: Overhead Conduit Level (Water-Pipe Duct) ---
        s.set_block(0, 4, z, "minecraft:deepslate_tiles")
        s.set_block(1, 4, z, "minecraft:deepslate_tiles")
        s.set_block(2, 4, z, "minecraft:polished_deepslate") # Outer casing
        # Central duct slot (x=3..4) for glass water pipe
        s.set_block(3, 4, z, "minecraft:glass")
        s.set_block(4, 4, z, "minecraft:glass")
        s.set_block(5, 4, z, "minecraft:polished_deepslate") # Outer casing
        s.set_block(6, 4, z, "minecraft:deepslate_tiles")
        s.set_block(7, 4, z, "minecraft:deepslate_tiles")

        # --- Y=5: Solid Roof Cap ---
        for x in range(width):
            s.set_block(x, 5, z, "minecraft:deepslate_bricks")

    return s

if __name__ == "__main__":
    corridor = build_deepslate_corridor_segment(length=5)
    print("Corridor Module Material Requirements:")
    for mat, count in corridor.get_material_requirements().items():
        print(f"  {mat}: {count}")
    
    print("\nVisual Layer Y=3 (Arches & Lighting):")
    print(corridor.render_ascii_slice(3))
    
    out_path = corridor.export_to_baritone("module_corridor_industrial_5z.schem")
    print(f"\nExported cleanly to Baritone: {out_path}")
