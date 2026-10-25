#!/usr/bin/env python3
"""Routing check against a real Claude Code run (manual; needs the claude CLI and API access).

For each scenario in routing-scenarios.json it starts `claude -p` in this repository with a
recording hook on Agent/Task/Skill (routing_probe_hook.py), file-changing and shell tools
disabled, and records which subagent or skill Claude picked first. Nothing is executed.

    python3 scripts/agent/routing_check.py [--repeats 2] [--only 02,03] [--out report.md]

Results: PASS (all runs picked an accepted route), FAIL, or ERROR (the run did not finish,
which is never reported as PASS). Not part of CI: it needs credentials and spends tokens.
"""
from __future__ import annotations

import sys

sys.dont_write_bytecode = True

import argparse
import concurrent.futures
import json
import os
import shutil
import subprocess
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FORCE = "Delegate this task to the single best-matching project subagent with the Agent tool; do not do the work yourself. Task: "
DISALLOWED = ["Bash", "Edit", "Write", "MultiEdit", "NotebookEdit", "WebFetch", "WebSearch"]


def run_once(scenario: dict, workdir: str, index: int, model: str | None) -> dict:
    log = os.path.join(workdir, f"{scenario['id']}-{index}.jsonl")
    settings = os.path.join(workdir, f"{scenario['id']}-{index}.settings.json")
    hook = f'python3 "{os.path.join(ROOT, "scripts/agent/routing_probe_hook.py")}"'
    with open(settings, "w", encoding="utf-8") as fh:
        json.dump({"hooks": {"PreToolUse": [{"matcher": "Agent|Task|Skill", "hooks": [{"type": "command", "command": hook}]}]}}, fh)
    prompt = (FORCE if scenario["delegate"] else "") + scenario["prompt"]
    cmd = ["claude", "-p", prompt, "--settings", settings, "--permission-mode", "acceptEdits",
           "--disallowedTools", *DISALLOWED, "--max-turns", "4", "--output-format", "json"]
    if model:
        cmd += ["--model", model]
    env = dict(os.environ, ROUTING_LOG=log)
    try:
        proc = subprocess.run(cmd, cwd=ROOT, env=env, capture_output=True, text=True, timeout=300, stdin=subprocess.DEVNULL)
    except subprocess.TimeoutExpired:
        return {"status": "ERROR", "detail": "timeout"}
    calls = []
    if os.path.exists(log):
        with open(log, encoding="utf-8") as fh:
            calls = [json.loads(line) for line in fh if line.strip()]
    cost = 0.0
    try:
        cost = json.loads(proc.stdout).get("total_cost_usd", 0.0)
    except ValueError:
        if not calls:
            return {"status": "ERROR", "detail": f"no result (exit {proc.returncode}): {proc.stderr.strip()[:120]}"}
    agents = [c["subagent_type"] for c in calls if c["tool"] in {"Agent", "Task"} and c.get("subagent_type")]
    skills = [c["skill"] for c in calls if c["tool"] == "Skill" and c.get("skill")]
    chosen = agents[0] if agents else None
    if scenario["delegate"]:
        ok = chosen in scenario["agents"]
        detail = f"agent={chosen or 'none'}" + (f" skills={skills}" if skills else "")
    else:
        project_agents = {f[:-3] for f in os.listdir(os.path.join(ROOT, ".claude/agents")) if f.endswith(".md")}
        delegated = [a for a in agents if a in project_agents]
        wanted = scenario.get("skills") or []
        ok = not delegated and (not wanted or not skills or any(s.split(":")[-1] in wanted for s in skills))
        detail = f"agent={delegated[0] if delegated else 'none (main session)'}" + (f" skills={skills}" if skills else "")
    return {"status": "PASS" if ok else "FAIL", "detail": detail, "cost": cost}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repeats", type=int, default=2)
    parser.add_argument("--only", default="", help="comma-separated scenario id prefixes")
    parser.add_argument("--out", default="")
    parser.add_argument("--model", default=None)
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--list", action="store_true")
    args = parser.parse_args()

    with open(os.path.join(ROOT, "scripts/agent/routing-scenarios.json"), encoding="utf-8") as fh:
        scenarios = json.load(fh)["scenarios"]
    if args.only:
        prefixes = tuple(args.only.split(","))
        scenarios = [s for s in scenarios if s["id"].startswith(prefixes)]
    if args.list:
        for s in scenarios:
            print(s["id"], "->", s["agents"] or "main session")
        return 0
    if not shutil.which("claude"):
        print("routing check: the claude CLI is not available; nothing was run (not a pass)", file=sys.stderr)
        return 2

    workdir = tempfile.mkdtemp(prefix="routing-check-")
    jobs = [(s, i) for s in scenarios for i in range(args.repeats)]
    results: dict[str, list[dict]] = {s["id"]: [] for s in scenarios}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        futures = {pool.submit(run_once, s, workdir, i, args.model): s["id"] for s, i in jobs}
        for future in concurrent.futures.as_completed(futures):
            results[futures[future]].append(future.result())

    lines = ["| scenario | expected | result | runs |", "|---|---|---|---|"]
    failed = errored = 0
    total_cost = 0.0
    for s in scenarios:
        runs = results[s["id"]]
        total_cost += sum(r.get("cost", 0.0) for r in runs)
        status = "ERROR" if any(r["status"] == "ERROR" for r in runs) else "PASS" if all(r["status"] == "PASS" for r in runs) else "FAIL"
        failed += status == "FAIL"
        errored += status == "ERROR"
        expected = ", ".join(s["agents"]) if s["agents"] else "main session"
        lines.append(f"| {s['id']} | {expected} | {status} | " + "; ".join(f"{r['status']} {r['detail']}" for r in runs) + " |")
    lines.append("")
    lines.append(f"{len(scenarios)} scenarios x {args.repeats} runs; PASS {len(scenarios) - failed - errored}, FAIL {failed}, ERROR {errored}; cost ~${total_cost:.2f}")
    report = "\n".join(lines)
    print(report)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(report + "\n")
    shutil.rmtree(workdir, ignore_errors=True)
    return 1 if failed or errored else 0


if __name__ == "__main__":
    sys.exit(main())
