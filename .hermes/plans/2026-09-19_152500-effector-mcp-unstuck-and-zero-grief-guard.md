# Effector MCP: Zero-Grief Guard & Self-Healing Unstuck Protocol Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Implement an autonomous, multiplayer-safe Zero-Grief Guard and a multi-stage Self-Healing Unstuck Protocol (with AABB micro-escape scan, immutable barrier detection, and server-claim rubberband watchdog) inside the Effector MCP Fabric mod runtime.

**Architecture:** The client-side runtime layer handles all safety and spatial recovery autonomously. When pathing fails or stalls ($\Delta pos < 0.05$ over 40 ticks), the runtime executes a 5-phase Finite State Machine (FSM): (1) AABB micro-escape vector scan, (2) static hardness/barrier validation, (3) local spatial blacklisting, (4) controlled break test with rollback watchdog, and (5) structured error reporting to calling LLMs. High-value containers and base structures are protected by an immutable client-side guard.

**Tech Stack:** Minecraft 26.2 (Fabric Client), Java 21, Baritone API, Netty HTTP JSON-RPC MCP Server, Python simulation suite.

---

### Task 1: Zero-Grief Structural Container Guard in `PlayerActionController`

**Objective:** Block all destructive actions (`attack_block`, `mine_block`) targeting player storage, utility containers, and structural base components, returning structured refusal JSON.

**Files:**
- Modify: `src/client/java/eikarna/effector/action/PlayerActionController.java:385-438`
- Modify: `src/client/java/eikarna/effector/action/BaritoneController.java:18-50`
- Test: `scripts/test_zero_grief_guard.py`

**Step 1: Write verification test script**
Create `scripts/test_zero_grief_guard.py` sending `attack_block` to a chest coordinate (`-555, 55, -1608`) and verifying error response `{"error": "PROTECTED_BLOCK"}`.

**Step 2: Run test to verify failure**
Run: `python scripts/test_zero_grief_guard.py`
Expected: FAIL — `attack_block` currently executes `startDestroyBlock` without checking block type.

**Step 3: Implement Zero-Grief Guard**
In `PlayerActionController.java`, create `isProtectedBlock(BlockState state)`:
```java
private static final Set<String> PROTECTED_BLOCKS = Set.of(
    "chest", "trapped_chest", "hopper", "furnace", "smoker", "blast_furnace",
    "barrel", "shulker_box", "crafting_table", "oak_door", "iron_door",
    "oak_trapdoor", "iron_trapdoor", "ladder", "glass"
);

public static boolean isProtectedBlock(BlockState state) {
    String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    return PROTECTED_BLOCKS.contains(name);
}
```
In `attackBlock()`:
```java
BlockState state = client.level.getBlockState(targetPos);
if (isProtectedBlock(state)) {
    JsonObject err = new JsonObject();
    err.addProperty("isError", true);
    err.addProperty("error", "PROTECTED_BLOCK");
    err.addProperty("message", "Action refused: Target block is a protected base asset (" + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + ")");
    err.addProperty("targetX", x);
    err.addProperty("targetY", y);
    err.addProperty("targetZ", z);
    return err;
}
```
In `BaritoneController.java`, enforce `blocksToDisallowBreaking` on initialization.

**Step 4: Run test to verify pass**
Run: `python scripts/test_zero_grief_guard.py`
Expected: PASS — returns `PROTECTED_BLOCK` and does not swing arm at chest.

**Step 5: Commit**
```bash
git add src/client/java/eikarna/effector/action/PlayerActionController.java src/client/java/eikarna/effector/action/BaritoneController.java
git commit -m "feat(safety): add zero-grief structural container guard"
```

---

### Task 2: Static Block Hardness & Barrier Verification in `attack_block`

**Objective:** Inspect block hardness and reject any destruction attempt on immutable blocks (`minecraft:barrier`, `minecraft:bedrock`, hardness < 0) before sending network packets.

**Files:**
- Modify: `src/client/java/eikarna/effector/action/PlayerActionController.java:400-435`
- Test: `scripts/test_immutable_block_rejection.py`

**Step 1: Write test script**
Create `scripts/test_immutable_block_rejection.py` testing rejection for blocks with `getDestroySpeed < 0`.

**Step 2: Run test to verify failure**
Run: `python scripts/test_immutable_block_rejection.py`
Expected: FAIL — currently sends destroy packet regardless of destroy speed.

**Step 3: Implement Hardness Check**
In `PlayerActionController.attackBlock()`:
```java
float destroySpeed = state.getDestroySpeed(client.level, targetPos);
if (destroySpeed < 0.0f) {
    JsonObject err = new JsonObject();
    err.addProperty("isError", true);
    err.addProperty("error", "IMMUTABLE_BLOCK");
    err.addProperty("message", "Target block is indestructible (hardness < 0: barrier, bedrock, or portal)");
    err.addProperty("blockType", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    err.addProperty("targetX", x);
    err.addProperty("targetY", y);
    err.addProperty("targetZ", z);
    return err;
}
```

**Step 4: Run test to verify pass**
Run: `python scripts/test_immutable_block_rejection.py`
Expected: PASS — immutable blocks return `IMMUTABLE_BLOCK` immediately.

**Step 5: Commit**
```bash
git add src/client/java/eikarna/effector/action/PlayerActionController.java
git commit -m "feat(safety): reject immutable blocks and barriers in attack_block"
```

---

### Task 3: AABB Micro-Escape Vector Engine in `AutonomousReflexController`

**Objective:** Implement a non-destructive 8-direction AABB collision scan that computes a collision-free micro-step vector $(\Delta x, \Delta z)$ of 0.4 blocks to unhook the avatar when stuck against corners.

**Files:**
- Modify: `src/client/java/eikarna/effector/action/AutonomousReflexController.java:50-120`
- Test: `scripts/test_micro_escape.py`

**Step 1: Write test script**
Create `scripts/test_micro_escape.py` simulating player collision in corner and verifying escape vector resolution.

**Step 2: Run test to verify failure**
Run: `python scripts/test_micro_escape.py`
Expected: FAIL — `AutonomousReflexController` only handles sprint jumping.

**Step 3: Implement `findEscapeVector`**
In `AutonomousReflexController.java`:
```java
public static Vec3 findEscapeVector(Minecraft client, LocalPlayer player) {
    if (client.level == null) return null;
    Vec3 pos = player.position();
    double[][] directions = {
        {0, -0.4}, {0, 0.4}, {0.4, 0}, {-0.4, 0},
        {0.3, -0.3}, {-0.3, -0.3}, {0.3, 0.3}, {-0.3, 0.3}
    };
    
    AABB playerBox = player.getBoundingBox();
    for (double[] dir : directions) {
        AABB testBox = playerBox.move(dir[0], 0.0, dir[1]);
        if (client.level.noCollision(player, testBox)) {
            return new Vec3(dir[0], 0.0, dir[1]);
        }
    }
    return null;
}
```

**Step 4: Run test to verify pass**
Run: `python scripts/test_micro_escape.py`
Expected: PASS — returns valid non-colliding vector when one exists.

**Step 5: Commit**
```bash
git add src/client/java/eikarna/effector/action/AutonomousReflexController.java
git commit -m "feat(locomotion): implement AABB micro-escape vector engine"
```

---

### Task 4: Autonomous Anti-Stuck FSM State Machine

**Objective:** Integrate stall detection (no movement for 40 ticks during active navigation) into `AutonomousReflexController` with automatic micro-escape execution, spatial blacklisting, and graceful abort.

**Files:**
- Modify: `src/client/java/eikarna/effector/action/AutonomousReflexController.java:121-200`
- Test: `scripts/test_antistuck_fsm.py`

**Step 1: Write test script**
Create `scripts/test_antistuck_fsm.py` monitoring stall counter and verifying FSM transitions.

**Step 2: Run test to verify failure**
Run: `python scripts/test_antistuck_fsm.py`
Expected: FAIL — no stall tracking currently exists.

**Step 3: Implement Stall Tracker & FSM**
In `AutonomousReflexController.java`:
- Maintain `stuckTicks`, `lastPos`, `stuckRecoveryState` (IDLE, NUDGING, REROUTING, ABORTED).
- On tick:
  - If Baritone is pathing and distance moved in 40 ticks < 0.05:
    - Attempt Phase 1: Apply `findEscapeVector` micro-nudge.
    - If no escape vector or still stuck after 2 attempts:
      - Phase 2: Check obstacle in front. If protected or barrier, abort Baritone pathing (`baritone.getPathingBehavior().cancel()`).
      - Phase 3: Set `lastStallError` with structured reason (`STUCK_IN_CORNER`, `PATH_BLOCKED_BY_BARRIER`).

**Step 4: Run test to verify pass**
Run: `python scripts/test_antistuck_fsm.py`
Expected: PASS — bot unhooks itself or cleanly cancels pathing with status recorded.

**Step 5: Commit**
```bash
git add src/client/java/eikarna/effector/action/AutonomousReflexController.java
git commit -m "feat(fsm): add autonomous anti-stuck watchdog and recovery state machine"
```

---

### Task 5: High-Signal MCP Navigation Contract & Error Reporting

**Objective:** Expose `navigate_to` MCP tool wrapping Baritone `goto` with active stall watchdog, timeout, and structured JSON diagnostics.

**Files:**
- Modify: `src/main/java/eikarna/effector/bridge/HTTPMCPServer.java:440-470`
- Modify: `src/main/java/eikarna/effector/server/MCPProtocol.java:180-220`
- Test: `scripts/test_navigate_contract.py`

**Step 1: Write test script**
Create `scripts/test_navigate_contract.py` verifying `navigate_to` schema, execution, and error payload format.

**Step 2: Run test to verify failure**
Run: `python scripts/test_navigate_contract.py`
Expected: FAIL — `navigate_to` tool not yet registered.

**Step 3: Implement `navigate_to`**
In `HTTPMCPServer.java`:
- Register tool `navigate_to` taking `x`, `y`, `z`, `timeout_seconds`.
- Returns structured JSON:
  ```json
  {
    "success": true,
    "status": "ARRIVED",
    "position": {"x": -554, "y": 55, "z": -1608}
  }
  ```
  Or on failure:
  ```json
  {
    "success": false,
    "status": "STALL_ABORTED",
    "reason": "IMMUTABLE_BARRIER | PROTECTED_BLOCK | CORNER_TRAP",
    "position": {"x": -556.7, "y": 56.0, "z": -1573.5},
    "recommendation": "Path blocked. Choose alternative waypoint."
  }
  ```

**Step 4: Run test to verify pass**
Run: `python scripts/test_navigate_contract.py`
Expected: PASS — `navigate_to` returns high-signal status for both success and blocked paths.

**Step 5: Commit**
```bash
git add src/main/java/eikarna/effector/bridge/HTTPMCPServer.java src/main/java/eikarna/effector/server/MCPProtocol.java
git commit -m "feat(mcp): add high-signal navigate_to tool with anti-stuck reporting"
```
