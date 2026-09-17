package eikarna.effector.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class ItemSerializer {

    public static JsonObject serializeItemStack(ItemStack stack, int slotIndex) {
        JsonObject obj = new JsonObject();
        if (slotIndex >= 0) {
            obj.addProperty("slot", slotIndex);
        }

        if (stack == null || stack.isEmpty()) {
            obj.addProperty("empty", true);
            return obj;
        }

        obj.addProperty("empty", false);
        obj.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        obj.addProperty("count", stack.getCount());
        obj.addProperty("max_stack_size", stack.getMaxStackSize());
        obj.addProperty("name", stack.getHoverName().getString());

        // Custom name (if renamed via anvil or tag)
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            obj.addProperty("custom_name", customName.getString());
        }

        // Durability / Damage
        if (stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getDamageValue();
            int remainingDurability = maxDamage - currentDamage;
            float durabilityPercent = ((float) remainingDurability / (float) maxDamage) * 100.0f;

            obj.addProperty("damage", currentDamage);
            obj.addProperty("max_damage", maxDamage);
            obj.addProperty("durability_remaining", remainingDurability);
            obj.addProperty("durability_percent", Math.round(durabilityPercent * 10.0f) / 10.0f);
        }

        // Enchantments on equipment/tools
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            JsonArray enchArray = new JsonArray();
            for (var entry : enchantments.entrySet()) {
                JsonObject enchObj = new JsonObject();
                enchObj.addProperty("id", entry.getKey().getRegisteredName());
                enchObj.addProperty("level", entry.getIntValue());
                try {
                    enchObj.addProperty("name", entry.getKey().value().description().getString());
                } catch (Exception ignored) {}
                enchArray.add(enchObj);
            }
            obj.add("enchantments", enchArray);
        }

        // Stored enchantments on Enchanted Books
        ItemEnchantments storedEnchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (storedEnchantments != null && !storedEnchantments.isEmpty()) {
            JsonArray storedArray = new JsonArray();
            for (var entry : storedEnchantments.entrySet()) {
                JsonObject enchObj = new JsonObject();
                enchObj.addProperty("id", entry.getKey().getRegisteredName());
                enchObj.addProperty("level", entry.getIntValue());
                try {
                    enchObj.addProperty("name", entry.getKey().value().description().getString());
                } catch (Exception ignored) {}
                storedArray.add(enchObj);
            }
            obj.add("stored_enchantments", storedArray);
        }

        // Lore lines
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            JsonArray loreArray = new JsonArray();
            for (Component line : lore.lines()) {
                loreArray.add(line.getString());
            }
            obj.add("lore", loreArray);
        }

        return obj;
    }
}
