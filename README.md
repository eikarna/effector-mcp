# Effector MCP

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric_%3E=_0.19.3-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25_%2B-orange.svg)](https://openjdk.org/)
[![Protocol](https://img.shields.io/badge/Protocol-MCP_JSON--RPC_2.0-purple.svg)](https://modelcontextprotocol.io/)
[![Modrinth](https://img.shields.io/badge/Modrinth-effector--mcp-00AF5C.svg)](https://modrinth.com/mod/effector-mcp)
[![Documentation](https://img.shields.io/badge/Docs-MkDocs_Material-teal.svg)](https://eikarna.github.io/effector-mcp/)

> **High-Performance Autonomous AI Agent Protocol Bridge for Minecraft 26.2**

Effector MCP transforms Minecraft into a first-class execution environment for autonomous LLMs and coding agents (**Claude Code**, **Claude Desktop**, **Hermes Agent**, **Cursor**, and custom Python/TypeScript harnesses). 

Running as a native Fabric client mod, it exposes an embedded JSON-RPC 2.0 Model Context Protocol (MCP) server directly inside the client engine on port `8080`.

---

## 📚 Documentation & Showcase

* 🌐 **Full Documentation**: [eikarna.github.io/effector-mcp](https://eikarna.github.io/effector-mcp/)
* 🎥 **Visual Showcase (10 Demos)**: [`docs/showcase.md`](docs/showcase.md)
* 📖 **Tutorials & Guides**: [`docs/tutorials/`](docs/tutorials/)

---

## 🌟 Core Highlights

* **🧭 Native 3D Voxel Pathfinding (`navigate_to`)**: Zero-dependency 3D A* navigation engine reading real block voxel collision shapes. Automatically handles 1-block auto-jumps, step-downs, multi-tier elevation, ceiling headroom clearance, and avoids hazardous blocks (Lava, Fire, Cacti, Sweet Berries, Wither Roses).
* **👁️ 360° Perception Radar (`scan_entities`)**: Spherical entity scanner detecting hostile/passive mobs, dropped items, and players with exact distance, 3D vectors, current/max health, armor, and item counts.
* **⚔️ Combat & Entity Actuators (`attack_entity`, `interact_entity`)**: Weapon-equipped melee attacks with reach validation (max 6.0 blocks) and wildlife interactions (villager trading, animal feeding/breeding, shearing, mounting).
* **⚡ 20 TPS Macro Action Queue (`execute_actions`, `cancel_actions`)**: Batch sequences of movement, rotations, item use, and interactions synchronized to engine ticks. Includes an instant emergency brake.
* **📦 Complete GUI & Crafting Suite**: Full control over 3x3 Crafting Tables, Anvils, Enchanting Tables, Brewing Stands, Furnaces, and Smokers with native container slot packet emulation.
* **📡 Zero-Polling SSE Event Streaming (`/events`)**: Real-time Server-Sent Events broadcasting chat messages, game events, and action queue completions with zero polling overhead.

---

## 🛠️ Tool Reference (22 Native Client Tools)

| Tool Name | Category | Description |
| :--- | :---: | :--- |
| `navigate_to` | Pathfinding | **Native 3D Voxel A***: Computes walkable route across elevation with auto-jump & hazard avoidance. |
| `scan_entities` | Perception | **Perception Radar**: 360° scan of mobs, items, and players with distance, HP, and filters. |
| `attack_entity` | Combat | Attacks target entity in range (max reach 6.0) with equipped weapon by `entity_id` or `uuid`. |
| `interact_entity` | Interaction | Interacts / uses held item on target entity (villager trade, feed/breed, mount, shear). |
| `get_player_info` | Perception | Returns player coordinates, camera yaw/pitch, health, food, armor, air, and full inventory. |
| `get_blocks_in_area` | Perception | Scans a 3D bounding box (up to 128 blocks per axis) with run-length compression. |
| `scan_chunk` | Perception | Scans voxel chunks (16x16xHeight) with altitude bounds and block filters. |
| `take_screenshot` | Vision | Captures current viewport / GUI frame as a high-resolution base64 PNG. |
| `set_player_look` | Motion | Sets absolute camera yaw and pitch angles (pitch clamped to `[-90, 90]`). |
| `look_at` | Motion | Computes trigonometry and aims camera directly at target coordinates (X, Y, Z). |
| `select_slot` | Motion | Selects active hotbar slot (0 to 8). |
| `swap_hands` | Motion | Swaps item between main hand and off-hand (emulates F key). |
| `use_item` | Motion | Right-clicks using current carried item in `main_hand` or `off_hand`. |
| `interact_block` | World | Right-click interaction on blocks (chests, workstations, levers, doors). |
| `attack_block` | World | Initiates attack or mining on targeted block coordinate. |
| `execute_actions` | Macro | **Macro Runner**: Executes timed movements, camera turns, and interactions in one shot. |
| `get_queue_status` | Macro | Returns active action queue status, current step, and remaining ticks. |
| `cancel_actions` | Macro | **Emergency Brake**: Instantly clears queued actions and releases all virtual movement keys. |
| `get_open_container` | Inventory | Reads active GUI container (slots, items, enchantment options, brewing ticks). |
| `click_container_slot`| Inventory | Emulates mouse clicks (`pickup`, `quick_move`, `swap`, `clone`, `throw`) inside container menus. |
| `click_container_button`| Inventory | Clicks interactive menu buttons (Enchanting Table tiers 0-2, Stonecutter). |
| `close_container` | Inventory | Closes currently active GUI menu and returns focus to world. |

---

## 🔌 Quickstart

### 1. Requirements
* Minecraft `26.2`
* Fabric Loader `>= 0.19.3`
* Java `25` or higher

### 2. Installation
1. Download `effector-mcp-1.1.0+mc26.2.jar` from [Modrinth](https://modrinth.com/mod/effector-mcp) or [GitHub Releases](https://github.com/eikarna/effector-mcp/releases).
2. Place the jar into your `.minecraft/mods` folder.
3. Launch Minecraft and load into any world. The MCP server starts automatically at `http://127.0.0.1:8080/mcp`.

### 3. Connect Your Agent

#### Claude Desktop (`claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "effector": {
      "url": "http://127.0.0.1:8080/mcp",
      "transport": "http"
    }
  }
}
```

#### Claude Code CLI:
```bash
claude mcp add effector http://127.0.0.1:8080/mcp
```

#### Python Example:
```python
import urllib.request, json

req = urllib.request.Request(
    "http://127.0.0.1:8080/mcp",
    data=json.dumps({
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {"name": "get_player_info", "arguments": {}},
        "id": 1
    }).encode(),
    headers={"Content-Type": "application/json", "Accept": "application/json"}
)

with urllib.request.urlopen(req) as resp:
    print(json.loads(resp.read().decode()))
```

---

## 📜 License

Licensed under the MIT License. Author: **eikarna**.
