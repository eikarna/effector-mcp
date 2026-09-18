# Installation

Setting up Effector MCP is quick and requires no complex build tools or native libraries on your game machine.

---

## Prerequisites

* **Minecraft Version**: `26.2`
* **Mod Loader**: [Fabric Loader](https://fabricmc.net/) `>= 0.19.3`
* **Fabric API**: Installed for Minecraft `26.2`
* **Java Runtime**: Java `25` or higher

---

## Step 1: Download Effector MCP

Download the latest release jar from Modrinth or GitHub:

* **Modrinth**: [modrinth.com/mod/effector-mcp](https://modrinth.com/mod/effector-mcp)
* **GitHub Releases**: [github.com/eikarna/effector-mcp/releases](https://github.com/eikarna/effector-mcp/releases)

Look for `effector-mcp-1.1.0+mc26.2.jar`.

---

## Step 2: Add to Mods Folder

Place the downloaded `.jar` file into your Minecraft instance `mods/` directory:

=== "Windows"
    ```
    %APPDATA%\.minecraft\mods    # Or in Prism / ElyPrism Launcher:
    %APPDATA%\PrismLauncher\instances\<instance>\.minecraft\mods    ```

=== "macOS"
    ```
    ~/Library/Application Support/minecraft/mods/
    ```

=== "Linux"
    ```
    ~/.minecraft/mods/
    ```

---

## Step 3: Launch & Verify

1. Launch Minecraft and load into any Singleplayer world (or multiplayer server with client-side permissions).
2. The MCP bridge server automatically starts on port `8080`.
3. Open your terminal or browser and test the endpoint:

```bash
curl -X POST http://127.0.0.1:8080/mcp   -H "Content-Type: application/json"   -H "Accept: application/json"   -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

If you receive a JSON response containing all 22 native tools, your installation is successful and ready for agent connection!
