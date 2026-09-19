package eikarna.effector.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public class PlayerInfoProvider implements eikarna.effector.utils.IPlayerInfoProvider {
    
    @Override
    public JsonObject getPlayerInfo() {
        return getPlayerInfoStatic();
    }

    public static JsonObject getPlayerInfoStatic() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        Level world = client.level;
        
        JsonObject playerInfo = new JsonObject();
        
        if (player == null) {
            playerInfo.addProperty("error", "No player found");
            if (client.gui != null && client.gui.screen() != null) {
                playerInfo.addProperty("currentScreen", client.gui.screen().getClass().getName());
                try {
                    playerInfo.addProperty("screenTitle", client.gui.screen().getTitle().getString());
                } catch (Throwable ignored) {}
            } else {
                playerInfo.addProperty("currentScreen", "null");
            }
            return playerInfo;
        }
        
        // Position information
        JsonObject position = new JsonObject();
        position.addProperty("x", player.getX());
        position.addProperty("y", player.getY());
        position.addProperty("z", player.getZ());
        playerInfo.add("position", position);
        
        // Block position (integer coordinates)
        JsonObject blockPos = new JsonObject();
        blockPos.addProperty("x", player.getBlockX());
        blockPos.addProperty("y", player.getBlockY());
        blockPos.addProperty("z", player.getBlockZ());
        playerInfo.add("blockPosition", blockPos);
        
        // Facing direction
        JsonObject rotation = new JsonObject();
        rotation.addProperty("yaw", player.getYRot());
        rotation.addProperty("pitch", player.getXRot());
        playerInfo.add("rotation", rotation);
        
        // Cardinal direction
        String direction = getCardinalDirection(player.getYRot());
        playerInfo.addProperty("facingDirection", direction);
        
        // Look vector
        Vec3 lookVec = player.getViewVector(1.0f);
        JsonObject lookVector = new JsonObject();
        lookVector.addProperty("x", lookVec.x);
        lookVector.addProperty("y", lookVec.y);
        lookVector.addProperty("z", lookVec.z);
        playerInfo.add("lookVector", lookVector);
        
        // Health and food information
        playerInfo.addProperty("health", player.getHealth());
        playerInfo.addProperty("maxHealth", player.getMaxHealth());
        playerInfo.addProperty("foodLevel", player.getFoodData().getFoodLevel());
        playerInfo.addProperty("saturation", player.getFoodData().getSaturationLevel());

        // Player Status & Abilities (Comprehensive Vitals)
        JsonObject status = new JsonObject();
        status.addProperty("armor_value", player.getArmorValue());
        status.addProperty("air_supply", player.getAirSupply());
        status.addProperty("max_air_supply", player.getMaxAirSupply());
        status.addProperty("is_sneaking", player.isShiftKeyDown());
        status.addProperty("is_sprinting", player.isSprinting());
        status.addProperty("is_swimming", player.isSwimming());
        status.addProperty("is_flying", player.getAbilities().flying);
        status.addProperty("may_fly", player.getAbilities().mayfly);
        status.addProperty("invulnerable", player.getAbilities().invulnerable);
        status.addProperty("is_on_fire", player.isOnFire());
        status.addProperty("is_in_water", player.isInWater());
        status.addProperty("is_sleeping", player.isSleeping());
        playerInfo.add("status", status);

        // Active Buffs / Debuffs (Potion Effects)
        JsonArray effectsArray = new JsonArray();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            JsonObject effObj = new JsonObject();
            effObj.addProperty("id", effect.getEffect().getRegisteredName());
            try {
                effObj.addProperty("name", effect.getEffect().value().getDisplayName().getString());
            } catch (Exception ignored) {}
            effObj.addProperty("duration_ticks", effect.getDuration());
            effObj.addProperty("duration_seconds", Math.round((effect.getDuration() / 20.0f) * 10.0f) / 10.0f);
            effObj.addProperty("amplifier", effect.getAmplifier());
            effObj.addProperty("level", effect.getAmplifier() + 1);
            effObj.addProperty("ambient", effect.isAmbient());
            effObj.addProperty("visible", effect.isVisible());
            effectsArray.add(effObj);
        }
        playerInfo.add("active_effects", effectsArray);
        
        // Game mode
        if (client.gameMode != null) {
            playerInfo.addProperty("gameMode", client.gameMode.getPlayerMode().name().toLowerCase());
        }
        
        // Dimension information
        if (world != null) {
            playerInfo.addProperty("dimension", world.dimension().identifier().toString());
            playerInfo.addProperty("timeOfDay", world.getDefaultClockTime());
            playerInfo.addProperty("isDay", world.isBrightOutside());
            playerInfo.addProperty("isNight", world.isDarkOutside());
        }
        
        // Player name
        playerInfo.addProperty("name", player.getName().getString());
        
        // Experience information
        playerInfo.addProperty("experienceLevel", player.experienceLevel);
        playerInfo.addProperty("experienceProgress", player.experienceProgress);
        playerInfo.addProperty("totalExperience", player.totalExperience);
        
        // Inventory & Equipment with Rich Details (Enchantments, Durability, Custom Names)
        JsonObject inventory = new JsonObject();
        int selectedSlot = player.getInventory().getSelectedSlot();
        inventory.addProperty("selectedSlot", selectedSlot);
        
        // Main hand & Offhand detailed
        inventory.add("mainHand", ItemSerializer.serializeItemStack(player.getMainHandItem(), selectedSlot));
        inventory.add("offHand", ItemSerializer.serializeItemStack(player.getOffhandItem(), 45));

        // Hotbar (Slots 0 to 8)
        JsonArray hotbarArray = new JsonArray();
        for (int i = 0; i < 9; i++) {
            hotbarArray.add(ItemSerializer.serializeItemStack(player.getInventory().getItem(i), i));
        }
        inventory.add("hotbar", hotbarArray);

        // Armor Equipment
        JsonObject armorObj = new JsonObject();
        armorObj.add("helmet", ItemSerializer.serializeItemStack(player.getItemBySlot(EquipmentSlot.HEAD), 39));
        armorObj.add("chestplate", ItemSerializer.serializeItemStack(player.getItemBySlot(EquipmentSlot.CHEST), 38));
        armorObj.add("leggings", ItemSerializer.serializeItemStack(player.getItemBySlot(EquipmentSlot.LEGS), 37));
        armorObj.add("boots", ItemSerializer.serializeItemStack(player.getItemBySlot(EquipmentSlot.FEET), 36));
        inventory.add("armor", armorObj);

        playerInfo.add("inventory", inventory);
        
        // --- 1. Reticle Focus (What the player is directly looking at) ---
        JsonObject lookingAt = new JsonObject();
        if (client.hitResult != null) {
            net.minecraft.world.phys.HitResult hit = client.hitResult;
            if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                BlockPos bPos = bhr.getBlockPos();
                var state = world != null ? world.getBlockState(bPos) : null;
                String blockId = state != null ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() : "unknown";
                double dist = Math.sqrt(player.getEyePosition().distanceToSqr(bhr.getLocation()));
                lookingAt.addProperty("type", "block");
                lookingAt.addProperty("block", blockId);
                lookingAt.addProperty("x", bPos.getX());
                lookingAt.addProperty("y", bPos.getY());
                lookingAt.addProperty("z", bPos.getZ());
                lookingAt.addProperty("face", bhr.getDirection().getName());
                lookingAt.addProperty("distance", Math.round(dist * 100.0) / 100.0);
                lookingAt.addProperty("can_reach", dist <= 4.5);
            } else if (hit instanceof net.minecraft.world.phys.EntityHitResult ehr && hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                net.minecraft.world.entity.Entity ent = ehr.getEntity();
                double dist = Math.sqrt(player.getEyePosition().distanceToSqr(ent.position()));
                lookingAt.addProperty("type", "entity");
                lookingAt.addProperty("entity_id", ent.getId());
                lookingAt.addProperty("entity_name", ent.getName().getString());
                lookingAt.addProperty("entity_type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType()).toString());
                lookingAt.addProperty("distance", Math.round(dist * 100.0) / 100.0);
                lookingAt.addProperty("can_reach", dist <= 3.5);
                if (ent instanceof net.minecraft.world.entity.LivingEntity le) {
                    lookingAt.addProperty("health", Math.round(le.getHealth() * 10.0f) / 10.0f);
                    lookingAt.addProperty("max_health", Math.round(le.getMaxHealth() * 10.0f) / 10.0f);
                }
            } else {
                lookingAt.addProperty("type", "miss");
                lookingAt.addProperty("description", "Looking at air or nothing within reach");
            }
        }
        playerInfo.add("looking_at", lookingAt);

        // --- 2. Immediate Proprioception (Surrounding blocks and vertical clearance) ---
        JsonObject bodyState = new JsonObject();
        if (world != null) {
            int px = player.getBlockX();
            int py = player.getBlockY();
            int pz = player.getBlockZ();

            var floorState = world.getBlockState(new BlockPos(px, py - 1, pz));
            var feetState = world.getBlockState(new BlockPos(px, py, pz));
            var head1State = world.getBlockState(new BlockPos(px, py + 1, pz));
            var head2State = world.getBlockState(new BlockPos(px, py + 2, pz));

            bodyState.addProperty("floor_beneath", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(floorState.getBlock()).toString());
            bodyState.addProperty("floor_solid", floorState.isSolid());
            bodyState.addProperty("feet_block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(feetState.getBlock()).toString());
            bodyState.addProperty("headspace_clear", head1State.isAir() && head2State.isAir());

            int ceilDist = -1;
            String ceilBlock = "none";
            for (int dy = 1; dy <= 16; dy++) {
                var s = world.getBlockState(new BlockPos(px, py + dy, pz));
                if (s.isSolid()) {
                    ceilDist = dy;
                    ceilBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
                    break;
                }
            }
            bodyState.addProperty("ceiling_distance", ceilDist);
            bodyState.addProperty("ceiling_block", ceilBlock);
            bodyState.addProperty("light_level", world.getMaxLocalRawBrightness(player.blockPosition()));
        }
        playerInfo.add("body_state", bodyState);

        // --- 3. 8-Directional Distance Radar at eye level ---
        JsonObject radar = new JsonObject();
        if (world != null) {
            int px = player.getBlockX();
            int eyeY = (int) Math.floor(player.getEyeY());
            int pz = player.getBlockZ();

            int[][] dirs = {
                {0, -1},  // NORTH
                {1, -1},  // NORTHEAST
                {1, 0},   // EAST
                {1, 1},   // SOUTHEAST
                {0, 1},   // SOUTH
                {-1, 1},  // SOUTHWEST
                {-1, 0},  // WEST
                {-1, -1}  // NORTHWEST
            };
            String[] dirNames = {"NORTH", "NORTHEAST", "EAST", "SOUTHEAST", "SOUTH", "SOUTHWEST", "WEST", "NORTHWEST"};

            for (int i = 0; i < dirs.length; i++) {
                int dx = dirs[i][0];
                int dz = dirs[i][1];
                int hitDist = -1;
                String hitBlock = "air";

                for (int step = 1; step <= 16; step++) {
                    BlockPos checkPos = new BlockPos(px + (dx * step), eyeY, pz + (dz * step));
                    var s = world.getBlockState(checkPos);
                    if (s.isSolid()) {
                        hitDist = step;
                        hitBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().replace("minecraft:", "");
                        break;
                    }
                }

                JsonObject rDir = new JsonObject();
                rDir.addProperty("distance", hitDist != -1 ? hitDist : 16);
                rDir.addProperty("hit_solid", hitDist != -1);
                rDir.addProperty("block", hitBlock);
                radar.add(dirNames[i], rDir);
            }
        }
        playerInfo.add("radar_eye_level", radar);

        // --- 4. 2D Top-Down ASCII Floor Slice (11x11 Grid centered on player) ---
        if (world != null) {
            int px = player.getBlockX();
            int py = player.getBlockY() - 1; // standing floor
            int pz = player.getBlockZ();
            int r = 5; // 11x11

            StringBuilder sb = new StringBuilder();
            sb.append("\nTop-Down Floor Slice (11x11, Y=").append(py).append(", @ = player):\n");
            sb.append("Legend: [@] You | [#] Wall/Rock | [.] Wood/Path | [C] Chest | [T] Torch | [ ] Air/Drop | [?] Other\n");

            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx == 0 && dz == 0) {
                        sb.append("@ ");
                    } else {
                        BlockPos pos = new BlockPos(px + dx, py, pz + dz);
                        var s = world.getBlockState(pos);
                        if (s.isAir()) {
                            sb.append("  ");
                        } else {
                            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
                            if (name.contains("chest")) sb.append("C ");
                            else if (name.contains("torch")) sb.append("T ");
                            else if (name.contains("plank") || name.contains("wood") || name.contains("slab")) sb.append(". ");
                            else if (name.contains("stone") || name.contains("cobble") || name.contains("deepslate") || name.contains("brick")) sb.append("# ");
                            else if (name.contains("dirt") || name.contains("grass")) sb.append(", ");
                            else if (name.contains("water")) sb.append("~ ");
                            else if (name.contains("lava")) sb.append("! ");
                            else sb.append("? ");
                        }
                    }
                }
                sb.append("\n");
            }
            playerInfo.addProperty("ascii_map", sb.toString());
        }

        // --- 5. Nearby Dropped Items (within 24 blocks) ---
        if (world != null && player != null) {
            AABB dropBox = new AABB(
                player.getX() - 24, player.getY() - 16, player.getZ() - 24,
                player.getX() + 24, player.getY() + 16, player.getZ() + 24
            );
            List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, dropBox);
            JsonArray dropArray = new JsonArray();
            for (ItemEntity ie : items) {
                if (ie.isAlive() && dropArray.size() < 15) {
                    JsonObject d = new JsonObject();
                    d.addProperty("id", BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).toString());
                    d.addProperty("name", ie.getItem().getHoverName().getString());
                    d.addProperty("count", ie.getItem().getCount());
                    d.addProperty("distance", Math.round(ie.distanceTo(player) * 10.0) / 10.0);
                    JsonObject dpos = new JsonObject();
                    dpos.addProperty("x", Math.round(ie.getX() * 10.0) / 10.0);
                    dpos.addProperty("y", Math.round(ie.getY() * 10.0) / 10.0);
                    dpos.addProperty("z", Math.round(ie.getZ() * 10.0) / 10.0);
                    d.add("pos", dpos);
                    dropArray.add(d);
                }
            }
            playerInfo.add("nearby_drops", dropArray);

            // --- 6. Nearby Threats & Players ---
            List<LivingEntity> nearbyLiving = world.getEntitiesOfClass(LivingEntity.class, dropBox, e -> e != null && e.isAlive() && e != player);
            JsonArray threatArray = new JsonArray();
            JsonArray playerArray = new JsonArray();
            for (LivingEntity le : nearbyLiving) {
                if (le instanceof Player p) {
                    JsonObject po = new JsonObject();
                    po.addProperty("name", p.getName().getString());
                    po.addProperty("distance", Math.round(p.distanceTo(player) * 10.0) / 10.0);
                    po.addProperty("health", Math.round(p.getHealth() * 10.0) / 10.0f);
                    playerArray.add(po);
                } else if (le instanceof Mob m) {
                    boolean targetingMe = m.getTarget() == player;
                    boolean isHostile = m instanceof Enemy || targetingMe;
                    if (isHostile || targetingMe) {
                        JsonObject to = new JsonObject();
                        to.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString());
                        to.addProperty("name", m.getName().getString());
                        to.addProperty("distance", Math.round(m.distanceTo(player) * 10.0) / 10.0);
                        to.addProperty("targeting_you", targetingMe);
                        to.addProperty("health", Math.round(m.getHealth() * 10.0) / 10.0f);
                        threatArray.add(to);
                    }
                }
            }
            playerInfo.add("nearby_threats", threatArray);
            playerInfo.add("nearby_players", playerArray);
        }

        return playerInfo;
    }
    
    private static String getCardinalDirection(float yaw) {
        yaw = yaw % 360;
        if (yaw < 0) yaw += 360;
        if (yaw >= 337.5 || yaw < 22.5) return "South";
        if (yaw >= 22.5 && yaw < 67.5) return "Southwest";
        if (yaw >= 67.5 && yaw < 112.5) return "West";
        if (yaw >= 112.5 && yaw < 157.5) return "Northwest";
        if (yaw >= 157.5 && yaw < 202.5) return "North";
        if (yaw >= 202.5 && yaw < 247.5) return "Northeast";
        if (yaw >= 247.5 && yaw < 292.5) return "East";
        return "Southeast";
    }
}
