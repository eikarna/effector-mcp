import struct
import gzip
import os

def _write_varint(val: int) -> bytearray:
    buf = bytearray()
    while True:
        b = val & 0x7F
        val >>= 7
        if val != 0:
            buf.append(b | 0x80)
        else:
            buf.append(b)
            break
    return buf

class SpongeSchematic:
    """
    Pure Python Sponge v2 (.schem) builder for Minecraft 1.20/1.21+
    Compatible natively with Baritone #build <filename>.schem
    """
    def __init__(self, width: int, height: int, length: int, default_block: str = "minecraft:air"):
        self.width = width
        self.height = height
        self.length = length
        self.default_block = default_block
        
        # 3D grid: [y][z][x] -> string
        self.grid = [
            [[default_block for _ in range(width)] for _ in range(length)]
            for _ in range(height)
        ]

    def set_block(self, x: int, y: int, z: int, block_state: str):
        if 0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length:
            self.grid[y][z][x] = block_state

    def fill(self, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block_state: str):
        min_x, max_x = min(x1, x2), max(x1, x2)
        min_y, max_y = min(y1, y2), max(y1, y2)
        min_z, max_z = min(z1, z2), max(z1, z2)
        for y in range(max(0, min_y), min(self.height, max_y + 1)):
            for z in range(max(0, min_z), min(self.length, max_z + 1)):
                for x in range(max(0, min_x), min(self.width, max_x + 1)):
                    self.grid[y][z][x] = block_state

    def get_material_requirements(self) -> dict[str, int]:
        reqs = {}
        for y in range(self.height):
            for z in range(self.length):
                for x in range(self.width):
                    b = self.grid[y][z][x]
                    if b != "minecraft:air":
                        reqs[b] = reqs.get(b, 0) + 1
        return dict(sorted(reqs.items(), key=lambda item: item[1], reverse=True))

    def render_ascii_slice(self, y: int) -> str:
        if not (0 <= y < self.height):
            return f"Layer Y={y} out of bounds (0..{self.height-1})"
        
        # Map distinct blocks to single characters
        palette = sorted(list({self.grid[y][z][x] for z in range(self.length) for x in range(self.width)}))
        chars = ".#@%*=+OXHMWBSPL"
        char_map = {b: (chars[i] if i < len(chars) else '?') for i, b in enumerate(palette)}
        
        lines = [f"--- Layer Y={y} ({self.width}x{self.length}) ---"]
        for z in range(self.length):
            row = "".join(char_map[self.grid[y][z][x]] for x in range(self.width))
            lines.append(f"{z:02d} | {row}")
        lines.append("Palette legend:")
        for b, c in char_map.items():
            lines.append(f"  '{c}' -> {b}")
        return "\n".join(lines)

    def save(self, output_path: str):
        # Build palette
        palette = {"minecraft:air": 0}
        next_id = 1
        for y in range(self.height):
            for z in range(self.length):
                for x in range(self.width):
                    b = self.grid[y][z][x]
                    if b not in palette:
                        palette[b] = next_id
                        next_id += 1

        # Binary NBT construction
        buf = bytearray()
        buf.append(10) # TAG_Compound (Schematic)
        name = b"Schematic"
        buf.extend(struct.pack(">H", len(name)) + name)

        # Version: 2
        buf.append(3) # TAG_Int
        k = b"Version"
        buf.extend(struct.pack(">H", len(k)) + k + struct.pack(">i", 2))

        # DataVersion: 3953 (1.21+)
        buf.append(3)
        k = b"DataVersion"
        buf.extend(struct.pack(">H", len(k)) + k + struct.pack(">i", 3953))

        # Dimensions: Shorts
        for dim_name, val in [(b"Width", self.width), (b"Height", self.height), (b"Length", self.length)]:
            buf.append(2) # TAG_Short
            buf.extend(struct.pack(">H", len(dim_name)) + dim_name + struct.pack(">h", val))

        # Palette compound
        buf.append(10) # TAG_Compound
        k = b"Palette"
        buf.extend(struct.pack(">H", len(k)) + k)
        for block_name, pid in palette.items():
            buf.append(3) # TAG_Int
            bk = block_name.encode("utf-8")
            buf.extend(struct.pack(">H", len(bk)) + bk + struct.pack(">i", pid))
        buf.append(0) # TAG_End (Palette)

        # BlockData byte array
        block_bytes = bytearray()
        for y in range(self.height):
            for z in range(self.length):
                for x in range(self.width):
                    pid = palette[self.grid[y][z][x]]
                    block_bytes.extend(_write_varint(pid))

        buf.append(7) # TAG_Byte_Array
        k = b"BlockData"
        buf.extend(struct.pack(">H", len(k)) + k + struct.pack(">i", len(block_bytes)) + block_bytes)

        # End of Root Compound
        buf.append(0)

        # Compress to .schem
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        with gzip.open(output_path, "wb") as f:
            f.write(buf)

    def export_to_baritone(self, filename: str) -> str:
        if not filename.endswith(".schem"):
            filename += ".schem"
        target_dir = "C:/Users/Administrator/AppData/Roaming/ElyPrismLauncher/instances/26.2/minecraft/schematics"
        out_path = os.path.join(target_dir, filename)
        self.save(out_path)
        return out_path
