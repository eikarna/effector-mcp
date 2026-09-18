# Effector MCP

> **High-Performance Autonomous AI Agent Protocol Bridge for Minecraft 26.2**

Effector MCP transforms Minecraft into a first-class execution environment for autonomous LLMs and coding agents (Claude Code, Claude Desktop, Hermes Agent, Cursor, and custom Python/TypeScript orchestrators).

Unlike legacy automation tools that rely on intrusive bot libraries or headless protocols, Effector runs as a native, lightweight Fabric client mod. It exposes an embedded JSON-RPC 2.0 Model Context Protocol (MCP) server directly within the client process.

---

## 🌟 Key Capabilities

=== "🧭 Native 3D Voxel Pathfinding"
    * **Zero Dependencies**: Pure Java 3D A* navigation engine reading real block voxel collision shapes.
    * **3D Geometry Aware**: Autonomous step-ups, step-downs, jump clearance validation, and lethal drop refusal (>3 blocks).
    * **Dynamic Hazard Avoidance**: Automatically routes around lava, fire, cactus, sweet berries, and wither roses.

=== "👁️ Perception Radar & Combat"
    * **360° Spherical Radar**: Query living mobs, hostile targets, dropped items, and players up to 128 blocks away.
    * **Rich Metadata**: Distance, exact vector, current/max health, armor items, and item stack counts.
    * **Full Melee Actuator**: Reach-validated melee swings with equipped weapons and passive wildlife interaction.

=== "⚡ Macro Action Queue"
    * **Engine-Tick Synchronized**: Execute sequences of camera turns, timed keyboard inputs, jumping, and sneaking at a solid 20 TPS.
    * **Emergency Brake**: Instantly cancel active queues and release virtual keys with `cancel_actions`.

=== "📦 Full Container & Crafting Suite"
    * **Complete GUI Control**: Crafting tables (3x3), Anvils, Enchanting Tables, Brewing Stands, Smokers, and Furnaces.
    * **Native Packet Emulation**: Seamless slot clicks, shifts, pickups, and button clicks.

=== "📡 Zero-Polling SSE Streaming"
    * **Real-Time Push (`/events`)**: Server-Sent Events stream chat messages, tick events, and action queue completions instantly.
    * **Zero Overhead**: Eliminates polling loops that waste model context and token quota.

---

## ⚡ Quick Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       AI Agent Core                         │
│       (Claude Code / Hermes / Cursor / Python SDK)          │
└───────────────┬─────────────────────────────▲───────────────┘
                │ HTTP POST /mcp              │ SSE GET /events
                ▼                             │ (Zero Polling)
┌─────────────────────────────────────────────┴───────────────┐
│              Effector MCP Server (Port 8080)                │
│    ┌───────────────────────────────────────────────────┐    │
│    │  JSON-RPC 2.0 Handler & EventBroadcaster Engine   │    │
│    └─────────┬───────────────────────────────▲─────────┘    │
│              ▼                               │              │
│    ┌──────────────────┐             ┌────────┴─────────┐    │
│    │ 22 Client Tools  │             │ Perception Radar │    │
│    │  & ActionQueue   │             │ & Voxel Navigator│    │
│    └─────────┬────────┘             └────────▲─────────┘    │
│              ▼                               │              │
│    ┌─────────────────────────────────────────┴─────────┐    │
│    │     Minecraft Fabric 26.2 Engine (Render Thread)  │    │
│    └───────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

Ready to empower your AI agent in Minecraft? Follow our [Quickstart Installation Guide](getting-started/installation.md) or explore the [Visual Showcase](showcase.md) to see Effector in action!
