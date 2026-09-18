package eikarna.effector.action;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PlayerActionController implements IPlayerActionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerActionController.class);

    private JsonObject runOnClientThread(ActionSupplier supplier) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Player or world is not available");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                JsonObject res = supplier.run(client, client.player);
                future.complete(res);
            } catch (Exception e) {
                LOGGER.error("Error executing player action on client thread", e);
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                future.complete(err);
            }
        });

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Action timed out: " + e.getMessage());
            return err;
        }
    }

    @FunctionalInterface
    private interface ActionSupplier {
        JsonObject run(Minecraft client, LocalPlayer player) throws Exception;
    }

    @Override
    public JsonObject setLook(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("yaw") || !arguments.has("pitch")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'yaw' and 'pitch'");
                return err;
            }

            float yaw = arguments.get("yaw").getAsFloat();
            float pitch = arguments.get("pitch").getAsFloat();

            pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
            yaw = yaw % 360.0f;
            if (yaw > 180.0f) yaw -= 360.0f;
            if (yaw < -180.0f) yaw += 360.0f;

            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.yHeadRot = yaw;
            player.yHeadRotO = yaw;
            player.yBodyRot = yaw;
            player.yBodyRotO = yaw;

            if (player.connection != null) {
                player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("yaw", yaw);
            res.addProperty("pitch", pitch);
            return res;
        });
    }

    @Override
    public JsonObject lookAt(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
                return err;
            }

            double targetX = arguments.get("x").getAsDouble();
            double targetY = arguments.get("y").getAsDouble();
            double targetZ = arguments.get("z").getAsDouble();

            double eyeX = player.getX();
            double eyeY = player.getEyeY();
            double eyeZ = player.getZ();

            double dx = targetX - eyeX;
            double dy = targetY - eyeY;
            double dz = targetZ - eyeZ;

            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            float pitch = (float) -(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI);

            yaw = yaw % 360.0f;
            if (yaw > 180.0f) yaw -= 360.0f;
            if (yaw < -180.0f) yaw += 360.0f;
            pitch = Math.max(-90.0f, Math.min(90.0f, pitch));

            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.yHeadRot = yaw;
            player.yHeadRotO = yaw;
            player.yBodyRot = yaw;
            player.yBodyRotO = yaw;

            if (player.connection != null) {
                player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("yaw", yaw);
            res.addProperty("pitch", pitch);
            res.addProperty("targetX", targetX);
            res.addProperty("targetY", targetY);
            res.addProperty("targetZ", targetZ);
            return res;
        });
    }

    @Override
    public JsonObject interactBlock(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
                return err;
            }

            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int z = arguments.get("z").getAsInt();

            Direction face = Direction.UP;
            if (arguments.has("direction")) {
                Direction parsed = Direction.byName(arguments.get("direction").getAsString().toLowerCase(Locale.ROOT));
                if (parsed != null) {
                    face = parsed;
                }
            }

            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (arguments.has("hand")) {
                String handStr = arguments.get("hand").getAsString().toLowerCase(Locale.ROOT);
                if (handStr.contains("off") || handStr.contains("second")) {
                    hand = InteractionHand.OFF_HAND;
                }
            }

            BlockPos targetPos = new BlockPos(x, y, z);
            Vec3 hitVec = Vec3.atCenterOf(targetPos);
            BlockHitResult hitResult = new BlockHitResult(hitVec, face, targetPos, false);

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            InteractionResult result = client.gameMode.useItemOn(player, hand, hitResult);

            JsonObject res = new JsonObject();
            res.addProperty("success", result.consumesAction());
            res.addProperty("result", result.toString());
            res.addProperty("targetX", x);
            res.addProperty("targetY", y);
            res.addProperty("targetZ", z);
            res.addProperty("face", face.getName());
            res.addProperty("hand", hand.name());
            return res;
        });
    }

    @Override
    public JsonObject attackBlock(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
                return err;
            }

            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int z = arguments.get("z").getAsInt();

            Direction face = Direction.UP;
            if (arguments.has("direction")) {
                Direction parsed = Direction.byName(arguments.get("direction").getAsString().toLowerCase(Locale.ROOT));
                if (parsed != null) {
                    face = parsed;
                }
            }

            BlockPos targetPos = new BlockPos(x, y, z);
            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            boolean started = client.gameMode.startDestroyBlock(targetPos, face);
            boolean destroyed = client.gameMode.destroyBlock(targetPos);

            JsonObject res = new JsonObject();
            res.addProperty("success", started || destroyed);
            res.addProperty("started", started);
            res.addProperty("destroyed", destroyed);
            res.addProperty("targetX", x);
            res.addProperty("targetY", y);
            res.addProperty("targetZ", z);
            return res;
        });
    }

    @Override
    public JsonObject attackEntity(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            Entity target = findEntity(client, arguments);
            if (target == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity not found. Specify valid 'entity_id' (int) or 'uuid' (string)");
                return err;
            }

            if (!target.isAlive()) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity is dead or despawned");
                return err;
            }

            double dist = Math.sqrt(player.distanceToSqr(target));
            if (dist > 6.0) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity is out of reach (distance: " + (Math.round(dist * 100.0) / 100.0) + ", max reach: 6.0)");
                return err;
            }

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            client.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("attacked_entity_id", target.getId());
            res.addProperty("attacked_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
            res.addProperty("distance", Math.round(dist * 100.0) / 100.0);
            return res;
        });
    }

    @Override
    public JsonObject interactEntity(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            Entity target = findEntity(client, arguments);
            if (target == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity not found. Specify valid 'entity_id' (int) or 'uuid' (string)");
                return err;
            }

            if (!target.isAlive()) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity is dead or despawned");
                return err;
            }

            double dist = Math.sqrt(player.distanceToSqr(target));
            if (dist > 6.0) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity is out of reach (distance: " + (Math.round(dist * 100.0) / 100.0) + ", max reach: 6.0)");
                return err;
            }

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (arguments != null && arguments.has("hand")) {
                String handStr = arguments.get("hand").getAsString().toLowerCase(Locale.ROOT);
                if (handStr.contains("off") || handStr.contains("second")) {
                    hand = InteractionHand.OFF_HAND;
                }
            }

            EntityHitResult hitResult = new EntityHitResult(target, target.position());
            InteractionResult result = client.gameMode.interact(player, target, hitResult, hand);
            player.swing(hand);

            JsonObject res = new JsonObject();
            res.addProperty("success", result.consumesAction());
            res.addProperty("result", result.toString());
            res.addProperty("interacted_entity_id", target.getId());
            res.addProperty("interacted_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
            res.addProperty("hand", hand.name());
            return res;
        });
    }

    private Entity findEntity(Minecraft client, JsonObject arguments) {
        if (arguments == null || client.level == null) return null;
        if (arguments.has("entity_id")) {
            int id = arguments.get("entity_id").getAsInt();
            return client.level.getEntity(id);
        }
        if (arguments.has("uuid")) {
            String uuidStr = arguments.get("uuid").getAsString();
            try {
                UUID uuid = UUID.fromString(uuidStr);
                for (Entity e : client.level.entitiesForRendering()) {
                    if (e.getUUID().equals(uuid)) return e;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public JsonObject useItem(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (arguments != null && arguments.has("hand")) {
                String handStr = arguments.get("hand").getAsString().toLowerCase(Locale.ROOT);
                if (handStr.contains("off") || handStr.contains("second")) {
                    hand = InteractionHand.OFF_HAND;
                }
            }

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            InteractionResult result = client.gameMode.useItem(player, hand);

            JsonObject res = new JsonObject();
            res.addProperty("success", result.consumesAction());
            res.addProperty("result", result.toString());
            res.addProperty("hand", hand.name());
            res.addProperty("usedItem", hand == InteractionHand.MAIN_HAND 
                ? (player.getMainHandItem().isEmpty() ? "empty" : player.getMainHandItem().getItem().toString())
                : (player.getOffhandItem().isEmpty() ? "empty" : player.getOffhandItem().getItem().toString()));
            return res;
        });
    }

    @Override
    public JsonObject selectSlot(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("slot")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameter: 'slot' (0-8)");
                return err;
            }

            int slot = arguments.get("slot").getAsInt();
            if (slot < 0 || slot > 8) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Hotbar slot must be between 0 and 8, got " + slot);
                return err;
            }

            player.getInventory().setSelectedSlot(slot);
            if (player.connection != null) {
                player.connection.send(new ServerboundSetCarriedItemPacket(slot));
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("selectedSlot", slot);
            return res;
        });
    }

    @Override
    public JsonObject swapHands() {
        return runOnClientThread((client, player) -> {
            if (player.connection != null) {
                player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO,
                    Direction.DOWN
                ));
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Swapped items between main hand and second hand (off-hand)");
            res.addProperty("mainHandItem", player.getMainHandItem().isEmpty() ? "empty" : player.getMainHandItem().getItem().toString());
            res.addProperty("offHandItem", player.getOffhandItem().isEmpty() ? "empty" : player.getOffhandItem().getItem().toString());
            return res;
        });
    }
}
