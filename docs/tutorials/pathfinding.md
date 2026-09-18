# Native 3D Voxel Pathfinding

Effector includes a zero-dependency 3D A* pathfinder implemented in native Java (`VoxelPathfinder.java`).

---

## Basic Call (`navigate_to`)

To navigate to target coordinates $(X, Y, Z)$:

```json
{
  "name": "navigate_to",
  "arguments": {
    "x": 125,
    "y": 68,
    "z": -42,
    "sprint": true,
    "wait_completion": true
  }
}
```

---

## Physics & Safety Rules

* **Auto-Jump Clearance**: Evaluates headroom ($Y+2$) above the standing position and landing position. If a ceiling is lower than 2 blocks, jumping is disabled.
* **1.5-Block Obstacle Rejection**: Distinguishes between 1.0-block solids (jumpable) and 1.5-block fences/walls (unjumpable), routing around them automatically.
* **Liquid Safety**: Water and lava are not treated as walkable solid floors.
* **Fall Protection**: Drops greater than 3 blocks are considered lethal and pruned from the transition graph.
