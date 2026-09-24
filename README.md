# Effector MCP

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric_%3E=_0.19.3-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25_%2B-orange.svg)](https://openjdk.org/)
[![Protocol](https://img.shields.io/badge/Protocol-MCP_JSON--RPC_2.0-purple.svg)](https://modelcontextprotocol.io/)
[![Modrinth](https://img.shields.io/badge/Modrinth-effector--mcp-00AF5C.svg)](https://modrinth.com/mod/effector-mcp)
[![Documentation](https://img.shields.io/badge/Docs-MkDocs_Material-teal.svg)](https://eikarna.github.io/effector-mcp/)

> **High-Performance Autonomous AI Agent Protocol Bridge for Minecraft 26.2**

Effector MCP transforms Minecraft into a first-class execution environment for autonomous LLMs and coding agents (**Claude Code**, **Claude Desktop**, **Hermes Agent**, **Cursor**, and custom Python/TypeScript harnesses). 

Running as a native Fabric client mod, it exposes an embedded JSON-RPC 2.0 Model Context Protocol (MCP) server directly inside the client engine on port `8080` with **54 production-grade native tools**.

---

## 📚 Documentation & Showcase

* 🌐 **Full Documentation**: [eikarna.github.io/effector-mcp](https://eikarna.github.io/effector-mcp/)
* 🎥 **Visual Showcase**: [`docs/showcase.md`](docs/showcase.md)
* 🧪 **1k Chaos Gauntlet Report**: [`tests/gauntlet_report.json`](tests/gauntlet_report.json)

---

## 🌟 Core Highlights

* **⚡ 100% Zero-Scripting Philosophy**: Say goodbye to complex external Python/Node loop scripts. All compound primitives, container sorting, eating FSMs, building generators, and mining are fully integrated as single native MCP tools.
* **🛡️ Battle-Tested 1,000-Scenario Gauntlet**: Stress-tested across 5 chaos groups (Spatial & Geometry, Atomic Logistics, Biological Reflex, Recipe Synthesis, Kinematics) with synthetic latency, network jitter, and out-of-bounds inputs. **Result: 1,000/1,000 passed with 0 fatal crashes (0.0% fail rate)**.
* **🪓 Smart 0ms Auto-Tool Selector**: Automatically calculates block hardness and required tool tier in the same client tick, hot-swapping the best tool (Netherite > Diamond > Iron > Stone) before sending the break packet.
* **💧 Autonomous MLG Water Clutch**: 20 TPS survival reflex monitors fall velocity ($v_y < -0.7$). When approaching ground, automatically equips a water bucket, aims straight down, places water to eliminate fall damage, and scoops it back up.
* **🌲 Autonomous Vein Miner (`harvest_vein`)**: Mines contiguous tree trunks and ore veins in a single turn using breadth-first voxel exploration, auto-tool switching, and natural camera tracking.
* **📦 Smart Container Logistics (`open_container`, `deposit_container`, `withdraw_container`)**: Interacts with chests/barrels/crafting tables, awaits server menu synchronization, and batch moves items in a single turn without GUI desync.
* **🧭 Dual Pathfinding Engine**: Native zero-dependency 3D Voxel A* (`navigate_to`) plus integrated full-featured Baritone pathfinding (`baritone_goto`, `baritone_mine`, `baritone_clear`).
* **📡 Zero-Polling SSE Event Streaming (`/events`)**: Real-time Server-Sent Events broadcasting chat messages, game events, and action queue completions.

---

## 🛠️ Tool Reference (54 Native Tools)

### 1. Autonomous Speedrunner & Survival Reflexes
| Tool Name | Description |
| :--- | :--- |
| `eat_food` | Selects best nutritional food from backpack/hotbar and sustains continuous eating until full. |
| `harvest_vein` | Autonomously mines an entire contiguous vein of blocks (tree logs, ores) up to `max_blocks`. |
| `open_container` | Interacts with container block at `(x, y, z)`, awaits GUI sync, and returns contents in one turn. |
| `build_structure` | Builds parametric Lego primitives (`wall`, `floor`, `pillar`, `arch`, `alcove`, `hollow_box`). |
| `seal_boundaries` | Scans room bounding planes, detects open breach holes, and patches them with specified material. |
| `respawn` | Respawns player if dead and immediately dismisses the death GUI screen. |
| `configure_reflexes` | Configures 20 TPS autonomous reflexes (`auto_eat`, `auto_defense`, `auto_loot`, `speedrunner_boost`). |
| `get_reflex_status` | Returns active states of all background survival reflexes. |

### 2. Spatial, World & Voxel Actuators
| Tool Name | Description |
| :--- | :--- |
| `get_block` | Fast single-block voxel inspector returning `blockType`, `isSolid`, `hardness`, and light levels. |
| `get_block_info` | Detailed inspection of block state properties and coordinates. |
| `get_blocks_in_area` | Bounding box scan with run-length compression for high voxel volumes. |
| `scan_chunk` | Scans entire 16x16 chunk with altitude bounds and block filtering. |
| `place_block` | Places block with reach verification ($\le 4.5$), support face detection, and humanized jitter. |
| `mine_block` | Mines block with auto-tool selection, reach validation, and safe-break protection. |
| `interact_block` | Interacts with workstations, levers, doors, and interactive tile entities. |
| `attack_block` | Attacks or taps block face. |
| `update_sign` | Sets text lines on targeted placed sign entity. |

### 3. Container & Inventory Logistics
| Tool Name | Description |
| :--- | :--- |
| `equip_item` | Atomically swaps item from backpack into active hotbar slot. |
| `swap_inventory_slots` | Swaps two inventory or crafting grid slot indices. |
| `select_slot` | Sets active hotbar slot index (0 to 8). |
| `swap_hands` | Swaps item between main hand and offhand. |
| `use_item` | Single-tick use/consume held item in main or offhand. |
| `get_open_container` | Inspects currently open menu (Chest, Furnace, Brewing Stand, Anvil, Enchanting Table). |
| `click_container_slot` | Clicks slot in open menu (`pickup`, `quick_move`, `swap`, `clone`, `throw`). |
| `click_container_button` | Clicks menu button (Enchantment levels 0-2, Stonecutter recipe). |
| `close_container` | Closes active GUI container menu. |
| `deposit_container` | Deposits items from player inventory into open container via quick-move. |
| `withdraw_container` | Withdraws items from open container into player inventory via quick-move. |
| `craft_item` | Collects crafted items from crafting output slot 0 directly to inventory. |

### 4. Perception & Combat Radar
| Tool Name | Description |
| :--- | :--- |
| `scan_entities` | 360° spherical perception radar scanning mobs, items, and players with HP and vectors. |
| `attack_entity` | Melee attacks target entity with equipped weapon (validated within 6.0 reach). |
| `interact_entity` | Interacts with entity (villager trade, feed/breed animal, mount, shear). |
| `get_perceptual_radar` | 3-tier raycast radar (feet, eye, ceiling) for spatial awareness. |
| `audit_enclosure` | 3D BFS flood-fill inside rooms to verify structural integrity and lighting. |
| `get_orthographic_slice` | Generates 2D ASCII horizontal/vertical slice map for lightweight spatial context. |

### 5. Kinematics, Movement & Pathfinding
| Tool Name | Description |
| :--- | :--- |
| `navigate_to` | Native 3D Voxel A* pathfinder with auto-jump, elevation stepping, and hazard avoidance. |
| `set_player_look` | Sets camera yaw/pitch angles directly with automatic `[-90, 90]` pitch clamping. |
| `look_at` | Computes line-of-sight trigonometry and smoothly points camera at target coordinates. |
| `execute_actions` | Action queue: executes timed macro sequences of movement and interaction. |
| `cancel_actions` | Emergency brake: clears all queued actions and releases virtual keys. |
| `get_queue_status` | Inspects current action queue execution state. |

### 6. Baritone Automation
| Tool Name | Description |
| :--- | :--- |
| `baritone_goto` | Commands Baritone to navigate to target coordinates. |
| `baritone_mine` | Instructs Baritone to search and mine specified block types. |
| `baritone_clear` | Commands Baritone to clear all solid blocks in an area. |
| `baritone_stop` | Cancels all active Baritone operations. |
| `baritone_command` | Executes raw Baritone commands (`sel 1`, `sel 2`, `schematic`). |
| `baritone_status` | Returns Baritone execution and pathing state. |
| `build_schematic` | Constructs Sponge schematic structures in the world. |
| `follow_player` | Dynamically follows and walks behind target player. |

### 7. Engine & Session Management
| Tool Name | Description |
| :--- | :--- |
| `get_player_info` | Complete telemetry: coordinates, yaw/pitch, health, food, armor, air, and 36-slot inventory. |
| `take_screenshot` | Captures high-resolution frame as base64 PNG for multimodal vision models. |
| `execute_commands` | Executes allowed Minecraft slash commands sequentially. |
| `load_world` | Loads singleplayer world by folder name. |
| `connect_server` | Connects client to a multiplayer server by host and port. |

---

## 🔌 Quickstart

### 1. Requirements
* Minecraft `26.2`
* Fabric Loader `>= 0.19.3`
* Java `25` or higher

### 2. Installation
1. Download `effector-mcp-1.1.0+mc26.2.jar` from [GitHub Releases](https://github.com/eikarna/effector-mcp/releases).
2. Place the jar into your `.minecraft/mods` folder.
3. Launch Minecraft and connect to any server or singleplayer world. The MCP server starts automatically at `http://127.0.0.1:8080/mcp`.

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

#### Hermes Agent (`config.yaml`):
```yaml
mcp_servers:
  effector:
    url: "http://127.0.0.1:8080/mcp"
    type: "http"
```

#### Python Client Example:
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
MIT License. Created by [Eikarna](https://github.com/eikarna).
