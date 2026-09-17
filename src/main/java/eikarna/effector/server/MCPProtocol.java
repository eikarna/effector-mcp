package eikarna.effector.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eikarna.effector.config.MCPConfig;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MCPProtocol {
    private static final Set<String> DESCRIBABLE_COMMANDS = Set.copyOf(MCPConfig.DEFAULT_ALLOWED_COMMANDS);
    
    public static JsonArray getToolsListResponse(MCPConfig config) {
        return getToolsListResponse(config, true);
    }

    public static JsonArray getToolsListResponse(MCPConfig config, boolean includeScreenshotTool) {
        JsonArray tools = new JsonArray();
        List<String> configuredAllowedCommands = (config != null && config.getServer() != null && config.getServer().getAllowedCommands() != null)
            ? config.getServer().getAllowedCommands()
            : MCPConfig.DEFAULT_ALLOWED_COMMANDS;
        List<String> allowedCommands = filterAllowedCommandsForDescription(configuredAllowedCommands);
        String allowedCommandsText = allowedCommands.isEmpty() ? "(none configured)" : String.join(", ", allowedCommands);
        
        // Execute commands tool
        JsonObject executeCommandsTool = new JsonObject();
        executeCommandsTool.addProperty("name", "execute_commands");
        executeCommandsTool.addProperty("description",
            "Execute one or more Minecraft commands sequentially. " +
            "Allowed commands: " + allowedCommandsText + ".\n\n" +
            "Response schema highlights:\n" +
            "- top-level: totalCommands, acceptedCount, appliedCount, failedCount\n" +
            "- per command: status, accepted, applied, summary, chatMessages\n" +
            "- status values: applied, rejected_by_game, execution_error, timed_out, rejected_by_safety, unknown\n\n" +
            "BLOCK STATE SYNTAX (critical for quality builds):\n" +
            "- Doors: setblock X Y Z oak_door[facing=north,half=lower,hinge=left,open=false] then setblock X Y+1 Z oak_door[facing=north,half=upper,hinge=left,open=false]\n" +
            "- Stairs: setblock X Y Z oak_stairs[facing=east,half=bottom,shape=straight]\n" +
            "- Slabs: setblock X Y Z oak_slab[type=top] or [type=bottom] or [type=double]\n" +
            "- Trapdoors: setblock X Y Z oak_trapdoor[facing=north,half=top,open=false]\n" +
            "- Fences/Walls: placed adjacently they auto-connect\n" +
            "- Logs/Pillars: setblock X Y Z oak_log[axis=y] (y=vertical, x/z=horizontal)\n" +
            "- Glazed Terracotta: [facing=north/south/east/west]\n" +
            "- Beds: setblock X Y Z red_bed[facing=south,part=foot] then setblock X Y Z+1 red_bed[facing=south,part=head] (head goes in facing direction: south=+Z, north=-Z, east=+X, west=-X)\n" +
            "- Chests: setblock X Y Z chest[facing=north]\n" +
            "- Torches: torch (floor), wall_torch[facing=north] (wall)\n" +
            "- Lanterns: lantern[hanging=true/false]\n" +
            "- Glass Panes: auto-connect to adjacent blocks\n\n" +
            "FILL COMMAND SYNTAX:\n" +
            "- fill X1 Y1 Z1 X2 Y2 Z2 <block> [replace|hollow|outline|destroy|keep]\n" +
            "- 'hollow' fills outer shell with block, inner with air - great for rooms\n" +
            "- 'outline' fills only outer shell, keeps interior unchanged\n" +
            "- 'replace <oldBlock>' replaces only matching blocks\n" +
            "- fill X1 Y1 Z1 X2 Y2 Z2 air replace <block> - removes specific block type\n\n" +
            "BUILDING BEST PRACTICES:\n" +
            "1. Use get_player_info first to get your position and use ABSOLUTE coordinates (not relative ~)\n" +
            "2. Plan structure dimensions before building. Typical house: 7-11 wide, 5-7 tall, 9-13 deep\n" +
            "3. Build in order: foundation -> walls (use fill hollow) -> roof -> windows/doors -> interior -> decoration\n" +
            "4. Doors MUST have two blocks: lower half (half=lower) and upper half (half=upper) at Y+1\n" +
            "5. Windows: use glass_pane, not glass (panes look much better)\n" +
            "6. Roofs: use stairs with correct facing for sloped roofs, slabs for flat roofs\n" +
            "7. After building, ALWAYS verify with get_blocks_in_area to check for errors\n" +
            "8. Group related commands in one call (e.g., all wall commands together) for efficiency\n" +
            "9. Max fill volume: 32768 blocks per command (Minecraft limit). Max entities per summon: 10"
        );

        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject commandsProperty = new JsonObject();
        commandsProperty.addProperty("type", "array");
        commandsProperty.addProperty("description", "Array of Minecraft commands to execute without leading slash. Each command is executed sequentially. Per-command results include status, accepted/applied booleans, summary, and command-scoped chat messages.");
        commandsProperty.addProperty("minItems", 1);
        
        JsonObject commandsItems = new JsonObject();
        commandsItems.addProperty("type", "string");
        commandsProperty.add("items", commandsItems);
        
        JsonObject validateSafetyProperty = new JsonObject();
        validateSafetyProperty.addProperty("type", "boolean");
        validateSafetyProperty.addProperty("description", "Whether to validate command safety (default: true)");
        validateSafetyProperty.addProperty("default", true);
        
        properties.add("commands", commandsProperty);
        properties.add("validate_safety", validateSafetyProperty);
        inputSchema.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("commands");
        inputSchema.add("required", required);
        
        executeCommandsTool.add("inputSchema", inputSchema);
        tools.add(executeCommandsTool);
        
        // Get player info tool
        JsonObject getPlayerInfoTool = new JsonObject();
        getPlayerInfoTool.addProperty("name", "get_player_info");
        getPlayerInfoTool.addProperty("description",
            "Get current player position and world context. CALL THIS FIRST before any building task to get absolute coordinates.\n\n" +
            "Returns:\n" +
            "- blockPosition: {x,y,z} integer coordinates - USE THESE for setblock/fill commands\n" +
            "- position: {x,y,z} exact floating-point coordinates\n" +
            "- facingDirection: cardinal direction (North/South/East/West)\n" +
            "- frontPosition: {x,y,z} 3 blocks ahead of player - good starting point for builds\n" +
            "- gameMode, dimension, timeOfDay, health, foodLevel, inventory\n\n" +
            "IMPORTANT: Minecraft Y-axis is vertical (Y=64 is typical ground level). " +
            "Use blockPosition for command coordinates. " +
            "Build at frontPosition or offset from blockPosition using absolute coordinates for reliability."
        );
        
        JsonObject playerInfoInputSchema = new JsonObject();
        playerInfoInputSchema.addProperty("type", "object");
        
        JsonObject playerInfoProperties = new JsonObject();
        // No required parameters for this tool
        playerInfoInputSchema.add("properties", playerInfoProperties);
        
        getPlayerInfoTool.add("inputSchema", playerInfoInputSchema);
        tools.add(getPlayerInfoTool);
        
        // Get blocks in area tool
        JsonObject getBlocksInAreaTool = new JsonObject();
        getBlocksInAreaTool.addProperty("name", "get_blocks_in_area");
        int maxAreaSize = config != null ? config.getServer().getMaxAreaSize() : 48;
        getBlocksInAreaTool.addProperty("description",
            "Scan and return all non-air blocks in a rectangular area. Use this to VERIFY builds after construction.\n\n" +
            "Maximum " + maxAreaSize + " blocks per axis. Air blocks are excluded. " +
            "Returns compressed block data grouped by type with regions (connected areas) and single blocks.\n\n" +
            "USAGE: After building, scan the build area to verify:\n" +
            "- All walls are complete (no gaps)\n" +
            "- Doors have both upper and lower halves\n" +
            "- Roof is fully covered\n" +
            "- Windows are placed correctly\n" +
            "If you find errors, use execute_commands to fix them."
        );
        
        JsonObject blocksInputSchema = new JsonObject();
        blocksInputSchema.addProperty("type", "object");
        
        JsonObject blocksProperties = new JsonObject();
        
        // From position
        JsonObject fromProperty = new JsonObject();
        fromProperty.addProperty("type", "object");
        fromProperty.addProperty("description", "Starting position of the area to scan");
        JsonObject fromPosProperties = new JsonObject();
        JsonObject xProp = new JsonObject();
        xProp.addProperty("type", "integer");
        JsonObject yProp = new JsonObject();
        yProp.addProperty("type", "integer");
        JsonObject zProp = new JsonObject();
        zProp.addProperty("type", "integer");
        fromPosProperties.add("x", xProp);
        fromPosProperties.add("y", yProp);
        fromPosProperties.add("z", zProp);
        fromProperty.add("properties", fromPosProperties);
        JsonArray fromRequired = new JsonArray();
        fromRequired.add("x");
        fromRequired.add("y");
        fromRequired.add("z");
        fromProperty.add("required", fromRequired);
        
        // To position
        JsonObject toProperty = new JsonObject();
        toProperty.addProperty("type", "object");
        toProperty.addProperty("description", "Ending position of the area to scan");
        JsonObject toPosProperties = new JsonObject();
        toPosProperties.add("x", xProp);
        toPosProperties.add("y", yProp);
        toPosProperties.add("z", zProp);
        toProperty.add("properties", toPosProperties);
        JsonArray toRequired = new JsonArray();
        toRequired.add("x");
        toRequired.add("y");
        toRequired.add("z");
        toProperty.add("required", toRequired);
        
        blocksProperties.add("from", fromProperty);
        blocksProperties.add("to", toProperty);
        blocksInputSchema.add("properties", blocksProperties);
        
        JsonArray blocksRequiredFields = new JsonArray();
        blocksRequiredFields.add("from");
        blocksRequiredFields.add("to");
        blocksInputSchema.add("required", blocksRequiredFields);
        
        getBlocksInAreaTool.add("inputSchema", blocksInputSchema);
        tools.add(getBlocksInAreaTool);

        // 4. Set player look (Native Rotation)
        JsonObject setLookTool = new JsonObject();
        setLookTool.addProperty("name", "set_player_look");
        setLookTool.addProperty("description", "Directly set the player's yaw and pitch angles in degrees. Synchronizes instantly with client rendering and server networking without mouse emulation.");
        JsonObject setLookSchema = new JsonObject();
        setLookSchema.addProperty("type", "object");
        JsonObject setLookProps = new JsonObject();
        JsonObject playerYawProp = new JsonObject();
        playerYawProp.addProperty("type", "number");
        playerYawProp.addProperty("description", "Yaw angle in degrees (-180 to 180 or 0 to 360)");
        JsonObject playerPitchProp = new JsonObject();
        playerPitchProp.addProperty("type", "number");
        playerPitchProp.addProperty("description", "Pitch angle in degrees (-90 looking up to 90 looking down)");
        setLookProps.add("yaw", playerYawProp);
        setLookProps.add("pitch", playerPitchProp);
        setLookSchema.add("properties", setLookProps);
        JsonArray lookReq = new JsonArray();
        lookReq.add("yaw");
        lookReq.add("pitch");
        setLookSchema.add("required", lookReq);
        setLookTool.add("inputSchema", setLookSchema);
        tools.add(setLookTool);

        // 5. Look at coordinate
        JsonObject lookAtTool = new JsonObject();
        lookAtTool.addProperty("name", "look_at");
        lookAtTool.addProperty("description", "Calculate exact line-of-sight angles from player eyes and rotate view directly towards specific (X, Y, Z) coordinates.");
        JsonObject lookAtSchema = new JsonObject();
        lookAtSchema.addProperty("type", "object");
        JsonObject lookAtProps = new JsonObject();
        JsonObject lx = new JsonObject(); lx.addProperty("type", "number"); lx.addProperty("description", "Target X coordinate");
        JsonObject ly = new JsonObject(); ly.addProperty("type", "number"); ly.addProperty("description", "Target Y coordinate");
        JsonObject lz = new JsonObject(); lz.addProperty("type", "number"); lz.addProperty("description", "Target Z coordinate");
        lookAtProps.add("x", lx);
        lookAtProps.add("y", ly);
        lookAtProps.add("z", lz);
        lookAtSchema.add("properties", lookAtProps);
        JsonArray lookAtReq = new JsonArray();
        lookAtReq.add("x"); lookAtReq.add("y"); lookAtReq.add("z");
        lookAtSchema.add("required", lookAtReq);
        lookAtTool.add("inputSchema", lookAtSchema);
        tools.add(lookAtTool);

        // 6. Interact block
        JsonObject interactTool = new JsonObject();
        interactTool.addProperty("name", "interact_block");
        interactTool.addProperty("description", "Directly interact with (right-click) a block at (X, Y, Z) to open chests, use doors, press buttons, etc., without requiring mouse cursor raycasts.");
        JsonObject interactSchema = new JsonObject();
        interactSchema.addProperty("type", "object");
        JsonObject interactProps = new JsonObject();
        JsonObject ix = new JsonObject(); ix.addProperty("type", "integer"); ix.addProperty("description", "Block X coordinate");
        JsonObject iy = new JsonObject(); iy.addProperty("type", "integer"); iy.addProperty("description", "Block Y coordinate");
        JsonObject iz = new JsonObject(); iz.addProperty("type", "integer"); iz.addProperty("description", "Block Z coordinate");
        JsonObject idir = new JsonObject(); idir.addProperty("type", "string"); idir.addProperty("description", "Face to interact with (up, down, north, south, east, west; default 'up')");
        JsonObject ihand = new JsonObject(); ihand.addProperty("type", "string"); ihand.addProperty("description", "Hand to use ('main_hand' or 'off_hand'; default 'main_hand')");
        interactProps.add("x", ix);
        interactProps.add("y", iy);
        interactProps.add("z", iz);
        interactProps.add("direction", idir);
        interactProps.add("hand", ihand);
        interactSchema.add("properties", interactProps);
        JsonArray interactReq = new JsonArray();
        interactReq.add("x"); interactReq.add("y"); interactReq.add("z");
        interactSchema.add("required", interactReq);
        interactTool.add("inputSchema", interactSchema);
        tools.add(interactTool);

        // 7. Attack / break block
        JsonObject attackTool = new JsonObject();
        attackTool.addProperty("name", "attack_block");
        attackTool.addProperty("description", "Attack or break the block at (X, Y, Z).");
        JsonObject attackSchema = new JsonObject();
        attackSchema.addProperty("type", "object");
        JsonObject attackProps = new JsonObject();
        JsonObject ax = new JsonObject(); ax.addProperty("type", "integer"); ax.addProperty("description", "Block X coordinate");
        JsonObject ay = new JsonObject(); ay.addProperty("type", "integer"); ay.addProperty("description", "Block Y coordinate");
        JsonObject az = new JsonObject(); az.addProperty("type", "integer"); az.addProperty("description", "Block Z coordinate");
        JsonObject adir = new JsonObject(); adir.addProperty("type", "string"); adir.addProperty("description", "Face direction (default 'up')");
        attackProps.add("x", ax); attackProps.add("y", ay); attackProps.add("z", az); attackProps.add("direction", adir);
        attackSchema.add("properties", attackProps);
        JsonArray attackReq = new JsonArray();
        attackReq.add("x"); attackReq.add("y"); attackReq.add("z");
        attackSchema.add("required", attackReq);
        attackTool.add("inputSchema", attackSchema);
        tools.add(attackTool);

        // 8. Use item
        JsonObject useItemTool = new JsonObject();
        useItemTool.addProperty("name", "use_item");
        useItemTool.addProperty("description", "Use / consume / right-click the currently held item.");
        JsonObject useItemSchema = new JsonObject();
        useItemSchema.addProperty("type", "object");
        JsonObject useItemProps = new JsonObject();
        JsonObject uHand = new JsonObject(); uHand.addProperty("type", "string"); uHand.addProperty("description", "Hand ('main_hand' or 'off_hand'; default 'main_hand')");
        useItemProps.add("hand", uHand);
        useItemSchema.add("properties", useItemProps);
        useItemTool.add("inputSchema", useItemSchema);
        tools.add(useItemTool);

        // 9. Select slot
        JsonObject selectSlotTool = new JsonObject();
        selectSlotTool.addProperty("name", "select_slot");
        selectSlotTool.addProperty("description", "Change the selected hotbar slot (0 to 8).");
        JsonObject selectSlotSchema = new JsonObject();
        selectSlotSchema.addProperty("type", "object");
        JsonObject selectProps = new JsonObject();
        JsonObject slotProp = new JsonObject(); slotProp.addProperty("type", "integer"); slotProp.addProperty("description", "Hotbar slot index (0 to 8)");
        selectProps.add("slot", slotProp);
        selectSlotSchema.add("properties", selectProps);
        JsonArray selectReq = new JsonArray(); selectReq.add("slot");
        selectSlotSchema.add("required", selectReq);
        selectSlotTool.add("inputSchema", selectSlotSchema);
        tools.add(selectSlotTool);

        // 10. Get open container
        JsonObject getContainerTool = new JsonObject();
        getContainerTool.addProperty("name", "get_open_container");
        getContainerTool.addProperty("description", "List items and slot indices in the currently open GUI container (Chest, Double Chest, Crafting Table, Inventory).");
        JsonObject getContainerSchema = new JsonObject();
        getContainerSchema.addProperty("type", "object");
        getContainerSchema.add("properties", new JsonObject());
        getContainerTool.add("inputSchema", getContainerSchema);
        tools.add(getContainerTool);

        // 11. Click container slot
        JsonObject clickSlotTool = new JsonObject();
        clickSlotTool.addProperty("name", "click_container_slot");
        clickSlotTool.addProperty("description", "Click a slot in the open container. Supports left/right click, shift-click quick-move (to move between chest and player inventory), throw, clone, and swap.");
        JsonObject clickSlotSchema = new JsonObject();
        clickSlotSchema.addProperty("type", "object");
        JsonObject clickProps = new JsonObject();
        JsonObject cSlot = new JsonObject(); cSlot.addProperty("type", "integer"); cSlot.addProperty("description", "Slot index inside the container");
        JsonObject cBtn = new JsonObject(); cBtn.addProperty("type", "integer"); cBtn.addProperty("description", "Mouse button (0 for left, 1 for right; default 0)");
        JsonObject cMode = new JsonObject(); cMode.addProperty("type", "string"); cMode.addProperty("description", "Click mode ('pickup', 'quick_move', 'swap', 'clone', 'throw', 'pickup_all'; default 'pickup')");
        JsonObject cSlotId = new JsonObject(); cSlotId.addProperty("type", "integer"); cSlotId.addProperty("description", "Optional container ID");
        clickProps.add("slot", cSlot);
        clickProps.add("button", cBtn);
        clickProps.add("mode", cMode);
        clickProps.add("container_id", cSlotId);
        clickSlotSchema.add("properties", clickProps);
        JsonArray clickReq = new JsonArray(); clickReq.add("slot");
        clickSlotSchema.add("required", clickReq);
        clickSlotTool.add("inputSchema", clickSlotSchema);
        tools.add(clickSlotTool);

        // 12. Close container
        JsonObject closeContainerTool = new JsonObject();
        closeContainerTool.addProperty("name", "close_container");
        closeContainerTool.addProperty("description", "Close the open container menu or GUI screen.");
        JsonObject closeSchema = new JsonObject();
        closeSchema.addProperty("type", "object");
        closeSchema.add("properties", new JsonObject());
        closeContainerTool.add("inputSchema", closeSchema);
        tools.add(closeContainerTool);

                // Swap hands tool (F key)
        JsonObject swapHandsTool = new JsonObject();
        swapHandsTool.addProperty("name", "swap_hands");
        swapHandsTool.addProperty("description", "Swap items between the player's main hand and second hand (off-hand). Useful for equipping shields, torches, food, or blocks in the second hand.");
        JsonObject swapHandsSchema = new JsonObject();
        swapHandsSchema.addProperty("type", "object");
        swapHandsSchema.add("properties", new JsonObject());
        swapHandsTool.add("inputSchema", swapHandsSchema);
        tools.add(swapHandsTool);

                // 14. Scan chunk tool
        JsonObject scanChunkTool = new JsonObject();
        scanChunkTool.addProperty("name", "scan_chunk");
        scanChunkTool.addProperty("description", 
            "CHUNK RECONNAISSANCE: Scan an entire Minecraft chunk (16x16 horizontal) or multiple chunks up to radius 2 with custom Y levels and block filters. " +
            "Automatically groups contiguous blocks. Defaults to player's current chunk and [Y - 25 .. Y + 25] if omitted. " +
            "Use 'filter': ['chest', 'ore', 'ladder'] to only return matching blocks of interest.");
        JsonObject scanChunkSchema = new JsonObject();
        scanChunkSchema.addProperty("type", "object");
        JsonObject chunkProps = new JsonObject();
        JsonObject cX = new JsonObject(); cX.addProperty("type", "integer"); cX.addProperty("description", "Optional chunk X coordinate (default: player chunk)");
        JsonObject cZ = new JsonObject(); cZ.addProperty("type", "integer"); cZ.addProperty("description", "Optional chunk Z coordinate (default: player chunk)");
        JsonObject cRad = new JsonObject(); cRad.addProperty("type", "integer"); cRad.addProperty("description", "Radius in chunks: 0 = 1 chunk (16x16), 1 = 3x3 chunks (48x48), default 0");
        JsonObject cMinY = new JsonObject(); cMinY.addProperty("type", "integer"); cMinY.addProperty("description", "Minimum Y coordinate (default: player Y - 25)");
        JsonObject cMaxY = new JsonObject(); cMaxY.addProperty("type", "integer"); cMaxY.addProperty("description", "Maximum Y coordinate (default: player Y + 25)");
        JsonObject cFilter = new JsonObject(); cFilter.addProperty("type", "array");
        JsonObject filterItem = new JsonObject(); filterItem.addProperty("type", "string");
        cFilter.add("items", filterItem);
        cFilter.addProperty("description", "Optional list of block ID substrings to filter for (e.g. ['chest', 'ore', 'ladder'])");
        chunkProps.add("chunk_x", cX);
        chunkProps.add("chunk_z", cZ);
        chunkProps.add("radius", cRad);
        chunkProps.add("min_y", cMinY);
        chunkProps.add("max_y", cMaxY);
        chunkProps.add("filter", cFilter);
        scanChunkSchema.add("properties", chunkProps);
        scanChunkTool.add("inputSchema", scanChunkSchema);
        tools.add(scanChunkTool);

                // 15. Execute actions tool (ScratchPad / Macro execution)
        JsonObject executeActionsTool = new JsonObject();
        executeActionsTool.addProperty("name", "execute_actions");
        executeActionsTool.addProperty("description", "ACTION QUEUE: Execute sequenced movements and interactions in one shot seamlessly.");
        JsonObject actionsSchema = new JsonObject();
        actionsSchema.addProperty("type", "object");
        JsonObject actionsProps = new JsonObject();
        JsonObject actArr = new JsonObject();
        actArr.addProperty("type", "array");
        JsonObject actItem = new JsonObject();
        actItem.addProperty("type", "object");
        actArr.add("items", actItem);
        actArr.addProperty("description", "Array of sequenced action step objects to execute");
        JsonObject waitComp = new JsonObject();
        waitComp.addProperty("type", "boolean");
        waitComp.addProperty("description", "Whether to wait for the entire queue to complete before returning HTTP response (default: true, timeout: 30s)");
        actionsProps.add("actions", actArr);
        actionsProps.add("wait_completion", waitComp);
        actionsSchema.add("properties", actionsProps);
        JsonArray reqAct = new JsonArray(); reqAct.add("actions");
        actionsSchema.add("required", reqAct);
        executeActionsTool.add("inputSchema", actionsSchema);
        tools.add(executeActionsTool);

        // 16. Cancel actions tool (Emergency stop)
        JsonObject cancelActionsTool = new JsonObject();
        cancelActionsTool.addProperty("name", "cancel_actions");
        cancelActionsTool.addProperty("description", "EMERGENCY STOP: Immediately cancel all running and queued actions and release all movement keys.");
        JsonObject cancelSchema = new JsonObject();
        cancelSchema.addProperty("type", "object");
        cancelSchema.add("properties", new JsonObject());
        cancelActionsTool.add("inputSchema", cancelSchema);
        tools.add(cancelActionsTool);

        // 17. Get queue status
        JsonObject queueStatusTool = new JsonObject();
        queueStatusTool.addProperty("name", "get_queue_status");
        queueStatusTool.addProperty("description", "Inspect the current execution status of the action queue (running, remaining steps, total steps).");
        JsonObject statusSchema = new JsonObject();
        statusSchema.addProperty("type", "object");
        statusSchema.add("properties", new JsonObject());
        queueStatusTool.add("inputSchema", statusSchema);
        tools.add(queueStatusTool);

                // 18. Click container button tool (Enchanting Table, Stonecutter, Loom)
        JsonObject clickButtonTool = new JsonObject();
        clickButtonTool.addProperty("name", "click_container_button");
        clickButtonTool.addProperty("description", 
            "CONTAINER BUTTON CLICK: Click a menu button inside an active interactive container GUI. " +
            "Essential for selecting Enchantment options (button_id 0, 1, or 2 in Enchanting Table), " +
            "Stonecutter recipe selections, Loom banner pattern choices, and Lectern page flips.");
        JsonObject btnSchema = new JsonObject();
        btnSchema.addProperty("type", "object");
        JsonObject btnProps = new JsonObject();
        JsonObject bId = new JsonObject(); bId.addProperty("type", "integer"); bId.addProperty("description", "Button index (0, 1, or 2 for Enchanting Table options)");
        JsonObject btnCId = new JsonObject(); btnCId.addProperty("type", "integer"); btnCId.addProperty("description", "Optional container ID (defaults to current active container)");
        btnProps.add("container_id", btnCId);
        btnProps.add("button_id", bId);
        btnSchema.add("properties", btnProps);
        JsonArray reqBtn = new JsonArray(); reqBtn.add("button_id");
        btnSchema.add("required", reqBtn);
        clickButtonTool.add("inputSchema", btnSchema);
        tools.add(clickButtonTool);

        if (includeScreenshotTool) {
            // Take screenshot tool (client-only)
            JsonObject takeScreenshotTool = new JsonObject();
            takeScreenshotTool.addProperty("name", "take_screenshot");
            takeScreenshotTool.addProperty("description", "Capture a screenshot of the current Minecraft game screen. " +
                "This allows you to visually inspect the world, your builds, or the player's surroundings. " +
                "Optionally, you can specify coordinates and rotation to move the player and set their gaze before taking the screenshot. " +
                "IMPORTANT: If x, y, and z are provided, the player WILL be teleported to that location. " +
                "If yaw and pitch are provided, the player's camera direction WILL be changed. " +
                "Use this to get the perfect angle for inspecting structures.");

            JsonObject screenshotInputSchema = new JsonObject();
            screenshotInputSchema.addProperty("type", "object");

            JsonObject screenshotProperties = new JsonObject();

            JsonObject xCoord = new JsonObject();
            xCoord.addProperty("type", "number");
            xCoord.addProperty("description", "Optional X coordinate to teleport the player to");

            JsonObject yCoord = new JsonObject();
            yCoord.addProperty("type", "number");
            yCoord.addProperty("description", "Optional Y coordinate to teleport the player to");

            JsonObject zCoord = new JsonObject();
            zCoord.addProperty("type", "number");
            zCoord.addProperty("description", "Optional Z coordinate to teleport the player to");

            JsonObject yawProp = new JsonObject();
            yawProp.addProperty("type", "number");
            yawProp.addProperty("description", "Optional Yaw rotation (0 to 360, or -180 to 180) to set the player's horizontal view direction");

            JsonObject pitchProp = new JsonObject();
            pitchProp.addProperty("type", "number");
            pitchProp.addProperty("description", "Optional Pitch rotation (-90 to 90) to set the player's vertical view direction (looking down to up)");

            screenshotProperties.add("x", xCoord);
            screenshotProperties.add("y", yCoord);
            screenshotProperties.add("z", zCoord);
            screenshotProperties.add("yaw", yawProp);
            screenshotProperties.add("pitch", pitchProp);

            screenshotInputSchema.add("properties", screenshotProperties);
            takeScreenshotTool.add("inputSchema", screenshotInputSchema);
            tools.add(takeScreenshotTool);
        }
        
        return tools;
    }
    
    public static JsonObject createSuccessResponse(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", false);
        
        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", message);
        content.add(textContent);
        
        response.add("content", content);
        return response;
    }
    
    public static JsonObject createErrorResponse(String message, JsonObject meta) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", true);
        
        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", message);
        content.add(textContent);
        
        response.add("content", content);
        if (meta != null) {
            response.add("_meta", meta);
        }
        
        return response;
    }

    public static JsonObject createImageResponse(String base64Data, String mimeType) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", false);

        JsonArray content = new JsonArray();
        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "image");
        imageContent.addProperty("data", base64Data);
        imageContent.addProperty("mimeType", mimeType);
        content.add(imageContent);

        response.add("content", content);
        return response;
    }

    private static List<String> filterAllowedCommandsForDescription(List<String> configuredAllowedCommands) {
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String command : configuredAllowedCommands) {
            if (command == null) {
                continue;
            }
            String normalized = command.trim().toLowerCase();
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (!normalized.isEmpty() && DESCRIBABLE_COMMANDS.contains(normalized)) {
                filtered.add(normalized);
            }
        }
        return List.copyOf(filtered);
    }
}
