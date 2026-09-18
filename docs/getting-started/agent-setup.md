# Agent Configuration

Connect your favorite autonomous AI agent or harness to Effector MCP.

---

## Claude Desktop

Add Effector MCP to your `claude_desktop_config.json`:

=== "Windows"
    Path: `%APPDATA%\Claude\claude_desktop_config.json`

    ```json
    {
      "mcpServers": {
        "effector": {
          "url": "http://127.0.0.1:8080/mcp",
          "transport": "http"
        }
      }
    }
    ```

=== "macOS"
    Path: `~/Library/Application Support/Claude/claude_desktop_config.json`

    ```json
    {
      "mcpServers": {
        "effector": {
          "url": "http://127.0.0.1:8080/mcp",
          "transport": "http"
        }
      }
    }
    ```

---

## Claude Code CLI

Add Effector as an HTTP MCP server:

```bash
claude mcp add effector http://127.0.0.1:8080/mcp
```

---

## Hermes Agent / Custom Python Harness

You can interact directly with the JSON-RPC endpoint in Python:

```python
import urllib.request
import json

def call_mcp(tool_name: str, arguments: dict = None):
    if arguments is None:
        arguments = {}
    payload = json.dumps({
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {"name": tool_name, "arguments": arguments},
        "id": 1
    }).encode("utf-8")

    req = urllib.request.Request(
        "http://127.0.0.1:8080/mcp",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json"
        }
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

# Example: Get player coordinates
info = call_mcp("get_player_info")
print(info)
```

---

## Cursor IDE

In Cursor Settings -> MCP Servers -> Add Server:

* **Name**: `effector`
* **Type**: `SSE` or `HTTP`
* **URL**: `http://127.0.0.1:8080/mcp`
