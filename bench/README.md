# Token benchmark: ctags code navigation

Measures what the ctags index (`docs/agents/code-navigation.md`) does to an agent's token use, by running the same read-only questions headless against two arms and comparing what Claude Code records for each run.

| Arm | Default commit | What the agent has |
|---|---|---|
| before | `c05dd6a` | No "Code navigation" section in CLAUDE.md, no `tags` |
| after | `main` | The section and a freshly built `tags` |

Each arm is a git worktree, by default beside the checkout in `../beltworks-bench/` (set `BENCH_WORKTREES` to move it). `bench/` is deleted from both worktrees, so neither agent reads about the benchmark.

## Running it

Universal Ctags must be installed. Every run is a real `claude -p` session and costs real tokens: a full run is 6 tasks × 2 arms × 5 runs = 60 sessions, at about $0.30 each.

```sh
sh bench/run.sh                    # every task, 5 runs per arm
sh bench/run.sh -n 1 -t collision  # one task, one run per arm: a smoke test
sh bench/run.sh -b <rev> -a <rev> -m <model>
python3 bench/report.py bench/out/<stamp>   # re-run the report
```

The agent gets read and search tools only (`Read`, `Grep`, `Glob` and read-only shell commands), with no subagents, web or MCP servers, so every run's work is in its one transcript. The arms alternate which goes first, so drift over a long run hits both alike.

## Output

`bench/out/<stamp>/` (gitignored) holds:

- `before/` and `after/`: each run's `claude -p` JSON result and its transcript (`<task>-<n>.json`, `.jsonl`).
- `report.md`: medians per arm, for all tasks and per task.
- `runs.csv`: one row per run, for your own analysis.
- `answers.md`: every answer, grouped by task and run, to check quality.
- `arms.tsv`, `claude-version.txt`: what was compared.

## Reading the report

- **Read result chars** and **whole-file Reads vs ranged Reads**: the direct measure of "reads less".
- **tags lookups**: whether agents follow the instruction at all. If it stays at 0, fix the CLAUDE.md wording, not ctags.
- **input tok (all calls)**: the input processed across every API call. More grep-then-read turns can cancel out smaller reads, because each turn re-sends the context.
- **peak context**: the largest single call, which decides how soon a long session compacts.
- **cost $**: the overall figure, with cache reads and writes priced in.
- **answers.md**: a saving that comes with a worse answer isn't a saving.

Trust medians over 5 or more runs per task, not single runs: one run that wanders off can swamp a mean. Most files in `src/main/java` are under 300 lines, which the navigation doc lets agents read whole, so expect the effect mainly on tasks that touch the large classes (`BeltTileBlock`, `BeltContents`, `BeltEndBlockEntity`).

Settings outside the repo (your `~/.claude` CLAUDE.md and hooks, such as RTK) apply to both arms alike. Keep the model and the Claude Code version the same for runs you compare.

## Tasks

`tasks/<name>.txt`, one prompt each, all read-only questions about the Mod's code. Add one by adding a file. `-t <name>` runs only that task.
