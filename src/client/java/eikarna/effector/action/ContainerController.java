package eikarna.effector.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eikarna.effector.utils.ItemSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ContainerController implements IContainerController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerController.class);

    private JsonObject runOnClientThread(ContainerSupplier supplier) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Player is not available");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                JsonObject res = supplier.run(client, client.player);
                future.complete(res);
            } catch (Exception e) {
                LOGGER.error("Error executing container action on client thread", e);
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
    private interface ContainerSupplier {
        JsonObject run(Minecraft client, LocalPlayer player) throws Exception;
    }

    @Override
    public JsonObject getOpenContainer() {
        return runOnClientThread((client, player) -> {
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "No active container menu");
                return err;
            }

            JsonObject containerObj = new JsonObject();
            containerObj.addProperty("containerId", menu.containerId);
            containerObj.addProperty("menuType", menu.getClass().getSimpleName());
            containerObj.addProperty("totalSlots", menu.slots.size());

            String screenTitle = "Unknown";
            if (client.gui != null && client.gui.screen() != null) {
                screenTitle = client.gui.screen().getTitle().getString();
            }
            containerObj.addProperty("screenTitle", screenTitle);

            // Specialized Container Metadata (Enchanting Table, Brewing Stand, Furnace, Anvil)
            if (menu instanceof EnchantmentMenu enchMenu) {
                JsonArray options = new JsonArray();
                for (int i = 0; i < 3; i++) {
                    JsonObject opt = new JsonObject();
                    opt.addProperty("button_id", i);
                    opt.addProperty("xp_cost", enchMenu.costs[i]);
                    opt.addProperty("clue_level", enchMenu.levelClue[i]);
                    opt.addProperty("clue_id", enchMenu.enchantClue[i]);
                    options.add(opt);
                }
                containerObj.add("enchantment_options", options);
            } else if (menu instanceof BrewingStandMenu brewMenu) {
                JsonObject brewData = new JsonObject();
                brewData.addProperty("fuel", brewMenu.getFuel());
                brewData.addProperty("brewing_ticks", brewMenu.getBrewingTicks());
                containerObj.add("brewing_stand", brewData);
            } else if (menu instanceof AbstractFurnaceMenu furnaceMenu) {
                JsonObject furnaceData = new JsonObject();
                furnaceData.addProperty("is_lit", furnaceMenu.isLit());
                furnaceData.addProperty("lit_progress", furnaceMenu.getLitProgress());
                furnaceData.addProperty("burn_progress", furnaceMenu.getBurnProgress());
                containerObj.add("furnace_data", furnaceData);
            } else if (menu instanceof AnvilMenu anvilMenu) {
                JsonObject anvilData = new JsonObject();
                anvilData.addProperty("repair_cost", anvilMenu.getCost());
                containerObj.add("anvil_data", anvilData);
            }

            // Rich Item Serialization (Enchantments, Durability, Custom Names, Lore)
            JsonArray slotsArray = new JsonArray();
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    slotsArray.add(ItemSerializer.serializeItemStack(stack, i));
                }
            }
            containerObj.add("items", slotsArray);
            return containerObj;
        });
    }

    @Override
    public JsonObject clickSlot(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("slot")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameter: 'slot'");
                return err;
            }

            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "No active container menu");
                return err;
            }

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            int slotId = arguments.get("slot").getAsInt();
            int button = arguments.has("button") ? arguments.get("button").getAsInt() : 0;
            String modeStr = arguments.has("mode") ? arguments.get("mode").getAsString().toUpperCase(Locale.ROOT) : "PICKUP";

            ContainerInput input = switch (modeStr) {
                case "QUICK_MOVE", "SHIFT", "SHIFT_CLICK" -> ContainerInput.QUICK_MOVE;
                case "SWAP" -> ContainerInput.SWAP;
                case "CLONE", "MIDDLE" -> ContainerInput.CLONE;
                case "THROW", "DROP" -> ContainerInput.THROW;
                case "QUICK_CRAFT" -> ContainerInput.QUICK_CRAFT;
                case "PICKUP_ALL", "DOUBLE_CLICK" -> ContainerInput.PICKUP_ALL;
                default -> ContainerInput.PICKUP;
            };

            int containerId = menu.containerId;
            if (arguments.has("container_id")) {
                containerId = arguments.get("container_id").getAsInt();
            }

            client.gameMode.handleContainerInput(containerId, slotId, button, input, player);

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("containerId", containerId);
            res.addProperty("slot", slotId);
            res.addProperty("button", button);
            res.addProperty("mode", input.name());
            return res;
        });
    }

    @Override
    public JsonObject clickButton(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("button_id")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameter: 'button_id'");
                return err;
            }

            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "No active container menu");
                return err;
            }

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            int buttonId = arguments.get("button_id").getAsInt();
            int containerId = menu.containerId;
            if (arguments.has("container_id")) {
                containerId = arguments.get("container_id").getAsInt();
            }

            client.gameMode.handleInventoryButtonClick(containerId, buttonId);

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("containerId", containerId);
            res.addProperty("buttonId", buttonId);
            res.addProperty("menuType", menu.getClass().getSimpleName());
            return res;
        });
    }

    @Override
    public JsonObject closeContainer() {
        return runOnClientThread((client, player) -> {
            player.closeContainer();
            if (client.gui != null) {
                client.gui.setScreen(null);
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Container closed");
            return res;
        });
    }
}
