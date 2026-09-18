# Perception Radar & Combat

Empower your agent to perceive its surroundings and engage in combat or animal husbandry.

---

## 360° Perception Radar (`scan_entities`)

Scan for entities within a sphere around the player:

```json
{
  "name": "scan_entities",
  "arguments": {
    "radius": 32.0,
    "type": "hostile"
  }
}
```

### Supported Filters
* `all`: Returns all entities.
* `hostile`: Zombies, Skeletons, Creepers, Spiders, etc.
* `passive`: Cows, Sheep, Pigs, Chickens, Villagers.
* `item`: Dropped items on the ground with stack counts.
* `player`: Other players on multiplayer servers.
* Custom name or substring (e.g. `"Boss"` or `"cow"`).

---

## Combat Actuator (`attack_entity`)

Target an entity within 6.0 blocks:

```json
{
  "name": "attack_entity",
  "arguments": {
    "entity_id": 404
  }
}
```

---

## Entity Interaction (`interact_entity`)

Feed animals, trade with villagers, or mount horses:

```json
{
  "name": "interact_entity",
  "arguments": {
    "entity_id": 403,
    "hand": "main_hand"
  }
}
```
