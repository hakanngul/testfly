---
inclusion: auto
name: graphify
description: Use for scoped TestFly codebase discovery or change-impact analysis when the graph is available and current.
---

When `graphify-out/graph.json` exists and is current, use targeted `graphify query`, `explain`, or `affected` calls before unfamiliar cross-module changes. Otherwise inspect source directly. Do not rebuild the graph or read the full report for routine tasks; source signatures and verification remain authoritative.
