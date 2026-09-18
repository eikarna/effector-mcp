package eikarna.effector.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class EntityScanner implements IEntityScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityScanner.class);

    @Override
    public JsonObject scanEntities(JsonObject arguments) {
        return scanEntitiesStatic(arguments);
    }

    public static JsonObject scanEntitiesStatic(JsonObject arguments) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "World or player not available");
            return err;
        }

        double radius = 32.0;
        if (arguments != null && arguments.has("radius")) {
            radius = Math.max(1.0, Math.min(128.0, arguments.get("radius").getAsDouble()));
        }

        int limit = 50;
        if (arguments != null && arguments.has("limit")) {
            limit = Math.max(1, Math.min(200, arguments.get("limit").getAsInt()));
        }

        String typeFilter = null;
        if (arguments != null && arguments.has("type") && !arguments.get("type").isJsonNull()) {
            typeFilter = arguments.get("type").getAsString().toLowerCase(Locale.ROOT).trim();
        }

        Vec3 playerPos = client.player.position();
        AABB box = new AABB(
            playerPos.x - radius, playerPos.y - radius, playerPos.z - radius,
            playerPos.x + radius, playerPos.y + radius, playerPos.z + radius
        );

        List<Entity> entityList = client.level.getEntities(client.player, box, e -> e != null && e.isAlive());
        entityList.sort(Comparator.comparingDouble(e -> e.distanceToSqr(client.player)));

        JsonArray entitiesArray = new JsonArray();
        int matchingCount = 0;

        for (Entity e : entityList) {
            double dist = Math.sqrt(e.distanceToSqr(client.player));
            if (dist > radius) continue;

            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            String category = getCategory(e);

            if (typeFilter != null && !typeFilter.isEmpty() && !typeFilter.equals("all")) {
                if (!matchesFilter(category, typeId, e.getName().getString(), typeFilter)) {
                    continue;
                }
            }

            matchingCount++;
            if (entitiesArray.size() < limit) {
                JsonObject entObj = new JsonObject();
                entObj.addProperty("id", e.getId());
                entObj.addProperty("uuid", e.getUUID().toString());
                entObj.addProperty("name", e.getName().getString());
                entObj.addProperty("type", typeId);
                entObj.addProperty("category", category);
                entObj.addProperty("distance", Math.round(dist * 100.0) / 100.0);

                JsonObject posObj = new JsonObject();
                posObj.addProperty("x", Math.round(e.getX() * 100.0) / 100.0);
                posObj.addProperty("y", Math.round(e.getY() * 100.0) / 100.0);
                posObj.addProperty("z", Math.round(e.getZ() * 100.0) / 100.0);
                entObj.add("position", posObj);

                entObj.addProperty("yaw", Math.round(e.getYRot() * 10.0) / 10.0f);
                entObj.addProperty("pitch", Math.round(e.getXRot() * 10.0) / 10.0f);
                entObj.addProperty("on_ground", e.onGround());

                if (e instanceof LivingEntity living) {
                    JsonObject healthObj = new JsonObject();
                    healthObj.addProperty("current", Math.round(living.getHealth() * 10.0) / 10.0f);
                    healthObj.addProperty("max", Math.round(living.getMaxHealth() * 10.0) / 10.0f);
                    entObj.add("health", healthObj);
                }

                if (e instanceof ItemEntity itemEnt) {
                    var stack = itemEnt.getItem();
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    itemObj.addProperty("count", stack.getCount());
                    entObj.add("item", itemObj);
                }

                entitiesArray.add(entObj);
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("total_matching", matchingCount);
        result.addProperty("returned_count", entitiesArray.size());
        result.addProperty("scan_radius", radius);
        result.add("entities", entitiesArray);
        return result;
    }

    private static String getCategory(Entity e) {
        if (e instanceof Player) return "player";
        if (e instanceof Enemy) return "hostile";
        if (e instanceof Animal) return "passive";
        if (e instanceof ItemEntity) return "item";
        if (e instanceof Projectile) return "projectile";
        if (e instanceof LivingEntity) return "living";
        return "misc";
    }

    private static boolean matchesFilter(String category, String typeId, String name, String filter) {
        if (category.equalsIgnoreCase(filter)) return true;
        if (typeId.contains(filter)) return true;
        if (name != null && name.toLowerCase(Locale.ROOT).contains(filter)) return true;
        if ("mobs".equalsIgnoreCase(filter) && (category.equals("hostile") || category.equals("passive") || category.equals("living"))) return true;
        return false;
    }
}
