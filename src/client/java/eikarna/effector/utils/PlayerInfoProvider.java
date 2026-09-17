package eikarna.effector.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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
