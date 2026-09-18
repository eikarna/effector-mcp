# 3x3 Crafting & Containers

Effector provides complete native emulation of all Minecraft GUI containers.

---

## Opening a Container

Interact with the block in the world:

```json
{
  "name": "interact_block",
  "arguments": {
    "x": 0,
    "y": 64,
    "z": 1,
    "direction": "up"
  }
}
```

---

## Inspecting Container Slots (`get_open_container`)

Querying `get_open_container` returns the active menu type and slot contents:

```json
{
  "name": "get_open_container",
  "arguments": {}
}
```

Response snippet:
```json
{
  "containerId": 1,
  "menuType": "CraftingMenu",
  "totalSlots": 46,
  "items": [
    { "slot": 36, "id": "minecraft:gold_ingot", "count": 8, "name": "Gold Ingot" },
    { "slot": 37, "id": "minecraft:apple", "count": 1, "name": "Apple" }
  ]
}
```

---

## Crafting Grid Mapping (3x3)

In the standard Crafting Table:
* **Slot 0**: Crafted Output
* **Slots 1–9**: 3x3 Crafting Grid
  * 1, 2, 3 = Top row
  * 4, 5, 6 = Middle row
  * 7, 8, 9 = Bottom row
* **Slots 10–36**: Player Inventory
* **Slots 37–45**: Hotbar Slots

To craft a **Golden Apple**:
1. Pick up 8 Gold Ingots (`mode: pickup`).
2. Right-click (`button: 1`) on slots 1, 2, 3, 4, 6, 7, 8, 9 to distribute 1 ingot per slot.
3. Pick up Apple and place into slot 5.
4. Quick-move (`mode: quick_move`) output slot 0 into inventory!
