# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
"""Summarises a bench/run.sh output directory: per-run rows in runs.csv, and per-task and overall medians by arm,
printed and written to report.md.

    python3 bench/report.py bench/out/<stamp>
"""
import csv
import json
import statistics
import sys
from pathlib import Path

METRICS = [
    # key, heading, format
    ("cost", "cost $", "{:.4f}"),
    ("turns", "turns", "{:.0f}"),
    ("input_total", "input tok (all calls)", "{:,.0f}"),
    ("peak_context", "peak context", "{:,.0f}"),
    ("output", "output tok", "{:,.0f}"),
    ("tool_result_chars", "tool result chars", "{:,.0f}"),
    ("read_result_chars", "Read result chars", "{:,.0f}"),
    ("reads_whole", "whole-file Reads", "{:.0f}"),
    ("reads_ranged", "ranged Reads", "{:.0f}"),
    ("tag_lookups", "tags lookups", "{:.0f}"),
]


def result_text(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(c.get("text", "") for c in content if isinstance(c, dict))
    return ""


def transcript_metrics(path):
    m = dict(input_total=0, peak_context=0, tool_result_chars=0, read_result_chars=0,
             reads_whole=0, reads_ranged=0, tag_lookups=0)
    if not path.exists():
        return m
    seen, tools = set(), {}
    for line in path.read_text().splitlines():
        try:
            e = json.loads(line)
        except json.JSONDecodeError:
            continue
        msg = e.get("message") or {}
        content = msg.get("content")
        if e.get("type") == "assistant":
            usage, mid = msg.get("usage"), msg.get("id")
            # One API call can span several transcript lines that repeat its usage: count it once.
            if usage and mid not in seen:
                seen.add(mid)
                ctx = (usage.get("input_tokens", 0) + usage.get("cache_read_input_tokens", 0)
                       + usage.get("cache_creation_input_tokens", 0))
                m["input_total"] += ctx
                m["peak_context"] = max(m["peak_context"], ctx)
            for block in content if isinstance(content, list) else []:
                if block.get("type") != "tool_use":
                    continue
                name, inp = block.get("name"), block.get("input") or {}
                tools[block.get("id")] = name
                if name == "Read":
                    m["reads_ranged" if "offset" in inp or "limit" in inp else "reads_whole"] += 1
                elif name == "Bash" and "tags" in inp.get("command", "").split():
                    m["tag_lookups"] += 1
        elif e.get("type") == "user":
            for block in content if isinstance(content, list) else []:
                if block.get("type") != "tool_result":
                    continue
                size = len(result_text(block.get("content")))
                m["tool_result_chars"] += size
                if tools.get(block.get("tool_use_id")) == "Read":
                    m["read_result_chars"] += size
    return m


def run_row(arm, result_path):
    task, _, run = result_path.stem.rpartition("-")
    row = dict(arm=arm, task=task, run=int(run))
    try:
        r = json.loads(result_path.read_text())
    except (json.JSONDecodeError, OSError):
        r = {}
    row["ok"] = bool(r) and not r.get("is_error", False)
    row["cost"] = r.get("total_cost_usd", 0.0)
    row["turns"] = r.get("num_turns", 0)
    row["output"] = (r.get("usage") or {}).get("output_tokens", 0)
    row["answer"] = r.get("result", "")
    row.update(transcript_metrics(result_path.with_suffix(".jsonl")))
    return row


def median(rows, key):
    values = [r[key] for r in rows]
    return statistics.median(values) if values else 0


def table(groups, arms):
    head = "| metric | " + " | ".join(arms) + " | after vs before |"
    lines = [head, "|" + "---|" * (len(arms) + 2)]
    for key, heading, fmt in METRICS:
        meds = [median(groups.get(a, []), key) for a in arms]
        delta = ""
        if len(meds) == 2 and meds[0]:
            delta = f"{(meds[1] - meds[0]) / meds[0]:+.0%}"
        lines.append(f"| {heading} | " + " | ".join(fmt.format(v) for v in meds) + f" | {delta} |")
    return "\n".join(lines)


def main(out):
    out = Path(out)
    arms = [a for a in ("before", "after") if (out / a).is_dir()]
    rows = [run_row(a, p) for a in arms for p in sorted((out / a).glob("*.json"))]
    if not rows:
        sys.exit(f"no runs in {out}")

    fields = ["arm", "task", "run", "ok"] + [k for k, _, _ in METRICS]
    with open(out / "runs.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
    with open(out / "answers.md", "w") as f:
        for r in sorted(rows, key=lambda r: (r["task"], r["run"], r["arm"])):
            f.write(f"## {r['task']} · run {r['run']} · {r['arm']}\n\n{r['answer']}\n\n")

    good = [r for r in rows if r["ok"]]
    failed = len(rows) - len(good)
    arms_info = (out / "arms.tsv").read_text().strip().replace("\t", " @ ").replace("\n", ", ") \
        if (out / "arms.tsv").exists() else ""
    parts = [f"# Token benchmark: {out.name}", "",
             f"Arms: {arms_info}. Runs: {len(good)} ok, {failed} failed. Medians per run.", "",
             "## All tasks", "", table({a: [r for r in good if r["arm"] == a] for a in arms}, arms), ""]
    for task in sorted({r["task"] for r in good}):
        sub = [r for r in good if r["task"] == task]
        parts += [f"## {task}", "", table({a: [r for r in sub if r["arm"] == a] for a in arms}, arms), ""]
    parts.append("Check answers.md for quality before trusting a saving.")
    report = "\n".join(parts)
    (out / "report.md").write_text(report + "\n")
    print(report)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else sys.exit(__doc__))
