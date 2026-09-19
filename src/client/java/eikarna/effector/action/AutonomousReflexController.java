package eikarna.effector.action;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class AutonomousReflexController implements IReflexController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutonomousReflexController.class);
    private static AutonomousReflexController instance;

    private boolean autoEatEnabled = true;
    private boolean autoDefenseEnabled = true;
    private boolean autoLootEnabled = true;
    private boolean speedrunnerBoostEnabled = true;
    private boolean antiStuckEnabled = true;

    // Eating state machine
    private boolean isEating = false;
    private int eatingTicks = 0;
    private int originalSlotBeforeEat = -1;

    // Combat & evasion state machine
    private int attackCooldownTicks = 0;
    private int creeperDodgeTicks = 0;

    // Anti-stuck state machine
    private Vec3 lastStuckCheckPos = null;
    private int stallTicks = 0;
    private int recoveryStep = 0;
    private String lastStallReason = null;

    public static synchronized AutonomousReflexController getInstance() {
        if (instance == null) {
            instance = new AutonomousReflexController();
        }
        return instance;
    }

    public void onClientTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return;
        }

        LocalPlayer player = client.player;
        if (!player.isAlive()) {
            resetStates(client);
            return;
        }

        if (attackCooldownTicks > 0) attackCooldownTicks--;
        if (creeperDodgeTicks > 0) {
            creeperDodgeTicks--;
            if (creeperDodgeTicks == 0 && client.options != null) {
                client.options.keyDown.setDown(false);
            }
        }

        // 1. Auto-Eat Reflex (highest survival priority if starving/hungry)
        if (autoEatEnabled) {
            handleAutoEat(client, player);
        }

        // 2. Auto-Defense Reflex (if not currently busy eating)
        if (autoDefenseEnabled && !isEating) {
            handleAutoDefense(client, player);
        }

        // 3. Auto-Loot Reflex (collect floating items nearby)
        if (autoLootEnabled && !isEating && creeperDodgeTicks == 0) {
            handleAutoLoot(client, player);
        }

        // 4. Speedrunner Boost Reflex (sprint-jump / bunny-hopping across open terrain)
        if (speedrunnerBoostEnabled && !isEating && creeperDodgeTicks == 0) {
            handleSprintJump(client, player);
        }

        // 5. Autonomous Anti-Stuck Reflex (multi-stage non-destructive recovery)
        if (antiStuckEnabled && !isEating && creeperDodgeTicks == 0) {
            handleAntiStuck(client, player);
        }
    }

    private void handleAutoEat(Minecraft client, LocalPlayer player) {
        int foodLevel = player.getFoodData().getFoodLevel();

        if (isEating) {
            eatingTicks++;
            if (client.options != null) {
                client.options.keyUse.setDown(true);
            }

            // Check if done eating (finished or max time reached)
            if (eatingTicks >= 38 || foodLevel >= 20 || !isHoldingFood(player)) {
                if (client.options != null) {
                    client.options.keyUse.setDown(false);
                }
                isEating = false;
                eatingTicks = 0;
                if (originalSlotBeforeEat >= 0 && originalSlotBeforeEat <= 8) {
                    setPlayerSelectedSlot(player, originalSlotBeforeEat);
                }
                originalSlotBeforeEat = -1;
            }
            return;
        }

        // Trigger eat if hungry
        if (foodLevel <= 15 || (player.getHealth() < 18.0f && foodLevel < 20)) {
            int bestFoodSlot = findBestFoodSlot(player);
            if (bestFoodSlot != -1) {
                originalSlotBeforeEat = player.getInventory().getSelectedSlot();
                setPlayerSelectedSlot(player, bestFoodSlot);
                client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                if (client.options != null) {
                    client.options.keyUse.setDown(true);
                }
                isEating = true;
                eatingTicks = 0;
            }
        }
    }

    private boolean isHoldingFood(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        return !held.isEmpty() && held.get(DataComponents.FOOD) != null;
    }

    private int findBestFoodSlot(LocalPlayer player) {
        int bestSlot = -1;
        int maxNutrition = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.get(DataComponents.FOOD) != null) {
                var foodComp = stack.get(DataComponents.FOOD);
                int nutrition = foodComp != null ? foodComp.nutrition() : 1;
                if (nutrition > maxNutrition) {
                    maxNutrition = nutrition;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private void handleAutoDefense(Minecraft client, LocalPlayer player) {
        double defenseRadius = 4.2;
        AABB searchBox = new AABB(
            player.getX() - defenseRadius, player.getY() - 3, player.getZ() - defenseRadius,
            player.getX() + defenseRadius, player.getY() + 3, player.getZ() + defenseRadius
        );

        List<LivingEntity> nearbyLiving = player.level().getEntitiesOfClass(
            LivingEntity.class, searchBox,
            e -> e != null && e.isAlive() && e != player
        );

        LivingEntity primeThreat = null;
        double minDistance = Double.MAX_VALUE;

        // 1. Priority: Check last attacker that hurt the player
        LivingEntity lastAttacker = player.getLastHurtByMob();
        if (lastAttacker != null && lastAttacker.isAlive() && player.distanceToSqr(lastAttacker) <= defenseRadius * defenseRadius) {
            primeThreat = lastAttacker;
        } else {
            // 2. Scan nearby threats with Conditional Hostility rules
            for (LivingEntity entity : nearbyLiving) {
                if (isHostileThreat(player, entity)) {
                    double distSq = player.distanceToSqr(entity);
                    if (distSq < minDistance) {
                        minDistance = distSq;
                        primeThreat = entity;
                    }
                }
            }
        }

        if (primeThreat == null) return;

        // Creeper evasive protocol
        if (primeThreat instanceof Creeper creeper) {
            float swell = creeper.getSwelling(1.0f);
            double dist = player.distanceTo(creeper);
            if (dist < 3.8 || swell > 0.1f) {
                if (client.options != null) {
                    client.options.keyDown.setDown(true);
                    creeperDodgeTicks = 12;
                }
            }
        }

        // Enderman eye contact avoidance
        if (primeThreat instanceof EnderMan && !(primeThreat instanceof Mob mob && mob.getTarget() == player)) {
            return;
        }

        // Equip best weapon in hotbar (Sword > Axe)
        equipBestWeapon(player);

        // Face the target
        lookAtEntity(player, primeThreat);

        // Attack if in reach and cooldown is ready
        if (player.distanceTo(primeThreat) <= 3.8 && attackCooldownTicks <= 0) {
            if (player.getAttackStrengthScale(0.5f) >= 0.85f) {
                client.gameMode.attack(player, primeThreat);
                player.swing(InteractionHand.MAIN_HAND);
                attackCooldownTicks = 8;
            }
        }
    }

    private boolean isHostileThreat(LocalPlayer player, LivingEntity entity) {
        if (entity instanceof Player) {
            return false;
        }

        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

        // Zombified Piglin / Piglin: NEVER attack unless it's actively targeting player
        if (typeId.contains("piglin")) {
            if (entity instanceof Mob mob) {
                return mob.getTarget() == player;
            }
            return false;
        }

        // Enderman: only hostile if actively targeting player
        if (entity instanceof EnderMan enderman) {
            return enderman.getTarget() == player;
        }

        // Generic Mob check (Wolf, Spider, Iron Golem)
        if (entity instanceof Mob mob) {
            if (mob.getTarget() == player) {
                return true;
            }
        }

        // Unconditional hostiles (Zombie, Skeleton, Creeper, Phantom, Slime, Drowned, etc.)
        if (entity instanceof Enemy) {
            return true;
        }

        return false;
    }

    private void equipBestWeapon(LocalPlayer player) {
        int bestSlot = -1;
        float maxDamage = -1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
                if (name.contains("sword")) {
                    float dmg = 10.0f;
                    if (dmg > maxDamage) {
                        maxDamage = dmg;
                        bestSlot = i;
                    }
                } else if (name.contains("axe") && maxDamage < 5.0f) {
                    bestSlot = i;
                    maxDamage = 5.0f;
                }
            }
        }
        if (bestSlot != -1 && player.getInventory().getSelectedSlot() != bestSlot) {
            setPlayerSelectedSlot(player, bestSlot);
        }
    }

    private void setPlayerSelectedSlot(LocalPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        if (player.connection != null) {
            player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private void lookAtEntity(LocalPlayer player, Entity target) {
        Vec3 eyes = player.getEyePosition();
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 diff = targetCenter.subtract(eyes);
        double diffXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, diffXZ)));
        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    private void handleAutoLoot(Minecraft client, LocalPlayer player) {
        AABB lootBox = new AABB(
            player.getX() - 2.5, player.getY() - 1, player.getZ() - 2.5,
            player.getX() + 2.5, player.getY() + 2, player.getZ() + 2.5
        );

        List<ItemEntity> nearbyItems = player.level().getEntitiesOfClass(
            ItemEntity.class, lootBox,
            item -> item != null && item.isAlive() && item.onGround()
        );

        if (!nearbyItems.isEmpty()) {
            ItemEntity closest = nearbyItems.stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);

            if (closest != null && player.distanceTo(closest) > 0.8) {
                Vec3 motion = closest.position().subtract(player.position()).normalize().scale(0.12);
                player.push(motion.x, 0, motion.z);
            }
        }
    }

    private void handleSprintJump(Minecraft client, LocalPlayer player) {
        if (client.options == null) return;

        boolean isSprinting = player.isSprinting();
        boolean isMoving = player.getDeltaMovement().horizontalDistanceSqr() > 0.02;
        boolean onGround = player.onGround();
        boolean inLiquid = player.isInWater() || player.isInLava();
        boolean climbing = player.onClimbable();
        boolean crouching = player.isCrouching();

        if (isSprinting && isMoving && !inLiquid && !climbing && !crouching) {
            // Check headroom: avoid jumping if ceiling is too low (prevent bonking head in 2-block tunnels)
            net.minecraft.core.BlockPos headPos = player.blockPosition().above(2);
            boolean headRoomClear = client.level != null && client.level.getBlockState(headPos).isAir();

            if (headRoomClear) {
                if (onGround) {
                    client.options.keyJump.setDown(true);
                } else {
                    client.options.keyJump.setDown(false);
                }
                return;
            }
        }

        if (client.options.keyJump.isDown()) {
            client.options.keyJump.setDown(false);
        }
    }

    private void handleAntiStuck(Minecraft client, LocalPlayer player) {
        if (!antiStuckEnabled || client.level == null) return;

        BaritoneController bc = new BaritoneController();
        JsonObject bStatus = bc.getStatus();
        boolean isPathing = bStatus.has("is_pathing") && bStatus.get("is_pathing").getAsBoolean();
        if (!isPathing) {
            stallTicks = 0;
            recoveryStep = 0;
            lastStuckCheckPos = player.position();
            return;
        }

        Vec3 currentPos = player.position();
        if (lastStuckCheckPos == null) {
            lastStuckCheckPos = currentPos;
            stallTicks = 0;
            return;
        }

        double distSq = currentPos.distanceToSqr(lastStuckCheckPos);
        if (distSq < 0.0025) { // Moved less than 0.05 blocks
            stallTicks++;
        } else {
            stallTicks = 0;
            recoveryStep = 0;
            lastStuckCheckPos = currentPos;
            return;
        }

        // Trigger unstuck FSM when stalled for 40 ticks (2 seconds)
        if (stallTicks >= 40) {
            LOGGER.warn("Avatar stall detected for {} ticks at pos: ({}, {}, {}). Executing Unstuck FSM step {}",
                stallTicks, currentPos.x, currentPos.y, currentPos.z, recoveryStep);

            if (recoveryStep == 0) {
                // Phase 1: Micro-Escape Vector Nudge
                Vec3 escapeVec = findEscapeVector(client, player);
                if (escapeVec != null) {
                    LOGGER.info("Unstuck: applying non-destructive micro-escape nudge vector {}", escapeVec);
                    player.push(escapeVec.x, 0.1, escapeVec.z);
                    if (client.options != null) {
                        client.options.keyJump.setDown(true);
                    }
                    recoveryStep = 1;
                    stallTicks = 20; // 1 second observation window
                    return;
                } else {
                    recoveryStep = 1;
                }
            }

            if (recoveryStep == 1) {
                if (client.options != null) {
                    client.options.keyJump.setDown(false);
                }
                // Phase 2: Inspect obstacle in front of avatar
                net.minecraft.core.Direction nearestDir = player.getDirection();
                net.minecraft.core.BlockPos frontPos = player.blockPosition().relative(nearestDir);
                net.minecraft.world.level.block.state.BlockState frontState = client.level.getBlockState(frontPos);

                if (PlayerActionController.isProtectedBlock(frontState)) {
                    LOGGER.warn("Unstuck: Front obstacle is protected container {}. Aborting path to prevent grief.", frontPos);
                    lastStallReason = "BLOCKED_BY_PROTECTED_CONTAINER";
                    bc.stop();
                    recoveryStep = 0;
                    stallTicks = 0;
                    return;
                }

                float destroySpeed = frontState.getDestroySpeed(client.level, frontPos);
                if (destroySpeed < 0.0f) {
                    LOGGER.warn("Unstuck: Front obstacle is immutable (barrier/bedrock) at {}. Aborting path.", frontPos);
                    lastStallReason = "IMMUTABLE_BARRIER";
                    bc.stop();
                    recoveryStep = 0;
                    stallTicks = 0;
                    return;
                }

                // Phase 3: Abort path gracefully if corner trapped
                LOGGER.warn("Unstuck: corner trap detected at {}. Canceling Baritone path gracefully.", currentPos);
                lastStallReason = "CORNER_TRAP";
                bc.stop();
                recoveryStep = 0;
                stallTicks = 0;
            }
        }
    }

    public static Vec3 findEscapeVector(Minecraft client, LocalPlayer player) {
        if (client.level == null) return null;
        AABB playerBox = player.getBoundingBox();
        double[][] directions = {
            {0, -0.4}, {0, 0.4}, {0.4, 0}, {-0.4, 0},
            {0.3, -0.3}, {-0.3, -0.3}, {0.3, 0.3}, {-0.3, 0.3}
        };

        for (double[] dir : directions) {
            AABB testBox = playerBox.move(dir[0], 0.0, dir[1]);
            if (client.level.noCollision(player, testBox)) {
                return new Vec3(dir[0], 0.0, dir[1]);
            }
        }
        return null;
    }

    public String getLastStallReason() {
        return lastStallReason;
    }

    public void resetStallReason() {
        lastStallReason = null;
    }

    public void resetStates(Minecraft client) {
        isEating = false;
        eatingTicks = 0;
        originalSlotBeforeEat = -1;
        attackCooldownTicks = 0;
        creeperDodgeTicks = 0;
        if (client != null && client.options != null) {
            client.options.keyUse.setDown(false);
            client.options.keyDown.setDown(false);
            client.options.keyJump.setDown(false);
        }
    }

    public JsonObject configure(JsonObject arguments) {
        if (arguments != null) {
            if (arguments.has("auto_eat")) autoEatEnabled = arguments.get("auto_eat").getAsBoolean();
            if (arguments.has("auto_defense")) autoDefenseEnabled = arguments.get("auto_defense").getAsBoolean();
            if (arguments.has("auto_loot")) autoLootEnabled = arguments.get("auto_loot").getAsBoolean();
            if (arguments.has("speedrunner_boost")) speedrunnerBoostEnabled = arguments.get("speedrunner_boost").getAsBoolean();
        }
        return getStatus();
    }

    public JsonObject getStatus() {
        JsonObject res = new JsonObject();
        res.addProperty("auto_eat", autoEatEnabled);
        res.addProperty("auto_defense", autoDefenseEnabled);
        res.addProperty("auto_loot", autoLootEnabled);
        res.addProperty("speedrunner_boost", speedrunnerBoostEnabled);
        res.addProperty("is_eating", isEating);
        return res;
    }
}
