# Autonomous Sneak-Bridging

Building bridges across ravines, oceans, or the Nether without falling into the void.

---

## The Recipe

1. Equip building blocks in hotbar slot 0 (`select_slot: 0`).
2. Look backward and down towards the block edge (`yaw: 0.0, pitch: 75.0`).
3. Engage sneak mode while walking backward for 10 ticks.
4. Right-click the rear face of the newly exposed block (`direction: south`).
5. Repeat!

```json
{
  "name": "execute_actions",
  "arguments": {
    "actions": [
      { "type": "select_slot", "slot": 0 },
      { "type": "look", "yaw": 0.0, "pitch": 75.0 },
      { "type": "move", "sneak": true, "backward": true, "ticks": 10 },
      { "type": "interact", "x": 0, "y": 64, "z": 20, "direction": "south" },
      { "type": "wait", "ticks": 3 },
      { "type": "move", "sneak": true, "backward": true, "ticks": 10 },
      { "type": "interact", "x": 0, "y": 64, "z": 21, "direction": "south" }
    ],
    "wait_completion": true
  }
}
```
