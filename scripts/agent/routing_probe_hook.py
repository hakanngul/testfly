#!/usr/bin/env python3
"""Recording hook used only by routing_check.py.

Appends every Agent/Task/Skill call to $ROUTING_LOG as one JSON line and blocks it, so the
probe sees which subagent or skill Claude Code picks without running it.
"""
import json
import os
import sys

payload = json.load(sys.stdin)
tool_input = payload.get("tool_input") or {}
record = {
    "tool": payload.get("tool_name"),
    "subagent_type": tool_input.get("subagent_type"),
    "skill": tool_input.get("skill") or tool_input.get("name"),
    "from_subagent": payload.get("agent_type"),
}
with open(os.environ["ROUTING_LOG"], "a", encoding="utf-8") as handle:
    handle.write(json.dumps(record) + "\n")
sys.stderr.write("Routing probe: this call was recorded and not executed. Reply in one sentence stating what you would have done.\n")
sys.exit(2)
