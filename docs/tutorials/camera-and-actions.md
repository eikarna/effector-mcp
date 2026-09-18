# Camera & Physics Macros

Learn how to control the player's viewpoint and schedule multi-tick physical action macros.

---

## Aiming the Camera

You have two tools for controlling the player's gaze:

### 1. Absolute Angles (`set_player_look`)
Sets exact yaw and pitch angles. Pitch is clamped to `[-90.0, 90.0]` to prevent disorienting camera flips.

```json
{
  "name": "set_player_look",
  "arguments": {
    "yaw": 180.0,
    "pitch": 0.0
  }
}
```

### 2. Coordinate Trigonometry (`look_at`)
Computes vector mathematics and points the player's crosshairs directly at a target coordinate $(X, Y, Z)$:

```json
{
  "name": "look_at",
  "arguments": {
    "x": 10.5,
    "y": 64.0,
    "z": -25.5
  }
}
```

---

## The Action Queue (`execute_actions`)

Instead of making slow round-trip network calls for every single game tick, `execute_actions` allows you to batch movements into a fluid macro.

```json
{
  "name": "execute_actions",
  "arguments": {
    "actions": [
      {
        "type": "select_slot",
        "slot": 0
      },
      {
        "type": "move",
        "forward": true,
        "sprint": true,
        "ticks": 20
      },
      {
        "type": "jump"
      },
      {
        "type": "move",
        "forward": true,
        "sprint": true,
        "ticks": 20
      }
    ],
    "wait_completion": true
  }
}
```

### Emergency Brake (`cancel_actions`)
If an obstacle is detected or your agent needs to halt immediately, call `cancel_actions`. It flushes the queue and instantly releases all pressed movement keys.
