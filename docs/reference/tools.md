# Tool Reference

Effector MCP exposes 22 client-side native tools for autonomous AI agents. Every tool runs directly inside the Minecraft Fabric client engine on the main render thread.

---

## Perception & Vision Tools

### `get_player_info`
Returns real-time data about the local player's state.

* **Parameters**: None.
* **Returns**:
    * `name` (string): Player username.
    * `position` (object): Decimal coordinates `x`, `y`, `z`.
    * `blockPosition` (object): Discrete block coordinates `x`, `y`, `z`.
    * `yaw` (number): Horizontal camera rotation degrees.
    * `pitch` (number): Vertical camera rotation degrees.
    * `health` (number): Current health points (max 20.0).
    * `foodLevel` (integer): Food saturation level (max 20).
    * `armor` (integer): Total armor defense points.
    * `air` (integer): Remaining air supply ticks underwater.
    * `inventory` (array): List of items currently held in hotbar and main inventory.

---

### `get_blocks_in_area`
Scans a 3D bounding box of blocks between two opposite corners.

* **Parameters**:
    * `from` (object, required): Start corner `{ "x": int, "y": int, "z": int }`.
    * `to` (object, required): End corner `{ "x": int, "y": int, "z": int }`.
* **Returns**:
    * `total_blocks` (integer): Count of non-air blocks.
    * `blocks` (array): Compressed list of block types and coordinate regions.

---

### `scan_chunk`
Scans an entire voxel chunk column with customizable altitude bounds.

* **Parameters**:
    * `chunk_x` (integer, optional): Chunk X coordinate (defaults to player chunk).
    * `chunk_z` (integer, optional): Chunk Z coordinate (defaults to player chunk).
    * `min_y` (integer, optional): Lower altitude bound.
    * `max_y` (integer, optional): Upper altitude bound.
    * `filter` (string, optional): Substring filter for block IDs (e.g. `"ore"`).
* **Returns**: Run-length encoded voxel chunk data.

---

### `scan_entities`
Perception radar scanning for living mobs, items, projectiles, and players in a 360-degree sphere.

* **Parameters**:
    * `radius` (number, optional, default: 32.0): Search radius in blocks (1.0 to 128.0).
    * `type` (string, optional, default: "all"): Filter category (`all`, `hostile`, `passive`, `player`, `item`, `projectile`, or custom name).
    * `limit` (integer, optional, default: 50): Maximum number of entities to return.
* **Returns**:
    * `total_matching` (integer): Number of entities found.
    * `entities` (array): List of entity records containing `id`, `uuid`, `name`, `type`, `position`, `distance`, `health`, `max_health`, and `items`.

---

### `take_screenshot`
Captures the current Minecraft client viewport frame.

* **Parameters**: None.
* **Returns**:
    * `data` (string): Base64-encoded PNG image.
    * `mimeType` (string): `"image/png"`.
    * `width` (integer): Frame width (e.g. 1920).
    * `height` (integer): Frame height (e.g. 1080).

---

## Motion & Actuator Tools

### `set_player_look`
Sets absolute camera yaw and pitch angles.

* **Parameters**:
    * `yaw` (number, required): Horizontal rotation degrees (-180.0 to 180.0).
    * `pitch` (number, required): Vertical rotation degrees (-90.0 to 90.0, automatically clamped).
* **Returns**: `success` (boolean).

---

### `look_at`
Points the player's crosshairs directly at target world coordinates.

* **Parameters**:
    * `x` (number, required): Target X coordinate.
    * `y` (number, required): Target Y coordinate.
    * `z` (number, required): Target Z coordinate.
* **Returns**:
    * `success` (boolean): `true`.
    * `yaw` (number): Calculated yaw.
    * `pitch` (number): Calculated pitch.

---

### `select_slot`
Changes the active hotbar slot.

* **Parameters**:
    * `slot` (integer, required): Hotbar slot index (0 to 8).
* **Returns**: `success` (boolean), `selected_slot` (integer).

---

### `swap_hands`
Swaps the item in the player's main hand with the off-hand (emulates pressing F).

* **Parameters**: None.
* **Returns**: `success` (boolean).

---

### `use_item`
Right-clicks using the currently carried item in the specified hand.

* **Parameters**:
    * `hand` (string, optional, default: "main_hand"): `"main_hand"` or `"off_hand"`.
* **Returns**: `success` (boolean), `usedItem` (string).

---

## World Interaction Tools

### `interact_block`
Performs a right-click interaction on a specific block face.

* **Parameters**:
    * `x` (integer, required): Block X coordinate.
    * `y` (integer, required): Block Y coordinate.
    * `z` (integer, required): Block Z coordinate.
    * `direction` (string, optional, default: "up"): Face to interact with (`up`, `down`, `north`, `south`, `east`, `west`).
    * `hand` (string, optional, default: "main_hand"): Hand to use.
* **Returns**: `success` (boolean), `result` (string).

---

### `attack_block`
Initiates mining or left-click attack on a targeted block coordinate.

* **Parameters**:
    * `x` (integer, required): Block X coordinate.
    * `y` (integer, required): Block Y coordinate.
    * `z` (integer, required): Block Z coordinate.
    * `direction` (string, optional, default: "up"): Face direction.
* **Returns**: `success` (boolean), `destroyed` (boolean).

---

## Combat & Interaction Tools

### `attack_entity`
Attacks a targeted entity within reach distance (max 6.0 blocks) using the equipped weapon.

* **Parameters**:
    * `entity_id` (integer, optional): Entity network ID.
    * `uuid` (string, optional): Entity UUID.
* **Returns**: `success` (boolean), `damage_dealt` (boolean).

---

### `interact_entity`
Interacts with an entity (e.g. trading with Villagers, breeding animals, shearing sheep, or mounting horses).

* **Parameters**:
    * `entity_id` (integer, optional): Target entity ID.
    * `uuid` (string, optional): Target entity UUID.
    * `hand` (string, optional, default: "main_hand"): Hand to use.
* **Returns**: `success` (boolean).

---

## Pathfinding & Navigation Tools

### `navigate_to`
Executes native 3D Voxel A* pathfinding to calculate and traverse a safe route to the target position.

* **Parameters**:
    * `x` (number, required): Target X coordinate.
    * `y` (number, required): Target Y coordinate.
    * `z` (number, required): Target Z coordinate.
    * `sprint` (boolean, optional, default: true): Whether to sprint on flat ground.
    * `wait_completion` (boolean, optional, default: true): Blocks until the destination is reached.
    * `max_nodes` (integer, optional, default: 4000): Maximum search nodes to explore.
* **Returns**:
    * `success` (boolean): Whether route was found.
    * `path_blocks` (integer): Total route voxel length.
    * `actions_generated` (integer): Physical macro steps generated.
    * `reached_target` (boolean): Whether player arrived at target.

---

## Macro & Action Queue Tools

### `execute_actions`
Schedules and executes a sequence of timed physical actions.

* **Parameters**:
    * `actions` (array, required): Ordered list of action steps.
    * `wait_completion` (boolean, optional, default: true): Wait until execution finishes.
* **Supported Step Types**:
    * `move`: `{ "forward": bool, "backward": bool, "left": bool, "right": bool, "sprint": bool, "sneak": bool, "ticks": int }`
    * `jump`: Jump once.
    * `look`: `{ "yaw": float, "pitch": float }`
    * `look_at`: `{ "x": float, "y": float, "z": float }`
    * `interact`: `{ "x": int, "y": int, "z": int, "direction": string }`
    * `select_slot`: `{ "slot": int }`
    * `wait`: `{ "ticks": int }`
* **Returns**: `success` (boolean), `total_actions` (integer).

---

### `get_queue_status`
Queries the status of the background action queue runner.

* **Parameters**: None.
* **Returns**: `running` (boolean), `remaining_steps` (integer), `completed_steps` (integer), `current_step` (string).

---

### `cancel_actions`
Emergency stop mechanism: flushes all queued macro steps and releases all virtual movement keys.

* **Parameters**: None.
* **Returns**: `success` (boolean), `message` (string).

---

## Container & Inventory Tools

### `get_open_container`
Reads the state of the currently open GUI container menu.

* **Parameters**: None.
* **Returns**:
    * `containerId` (integer): Window ID (0 for inventory, >0 for opened blocks).
    * `menuType` (string): Menu class (`CraftingMenu`, `ChestMenu`, `AnvilMenu`, `FurnaceMenu`, etc.).
    * `totalSlots` (integer): Number of slots in container.
    * `items` (array): List of items occupying slots.

---

### `click_container_slot`
Simulates mouse clicks inside container slots.

* **Parameters**:
    * `slot` (integer, required): Slot index to click.
    * `button` (integer, optional, default: 0): Mouse button (0 for left, 1 for right).
    * `mode` (string, optional, default: "pickup"): Click mode (`pickup`, `quick_move`, `swap`, `clone`, `throw`).
    * `container_id` (integer, optional): Container ID (defaults to active container).
* **Returns**: `success` (boolean).

---

### `click_container_button`
Clicks interactive menu buttons (such as the 3 enchantment options in an Enchanting Table).

* **Parameters**:
    * `button_id` (integer, required): Button index (0, 1, or 2).
    * `container_id` (integer, optional): Container ID.
* **Returns**: `success` (boolean).

---

### `close_container`
Closes the currently active container menu and returns control to the world.

* **Parameters**: None.
* **Returns**: `success` (boolean).
