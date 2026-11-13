#!/bin/sh
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Runs every task in bench/tasks against two arms, each a git worktree at one commit, and keeps each run's result and
# transcript under bench/out/<stamp>/<arm>/ for report.py. "before" has no ctags index; "after" has the code-navigation
# docs and a fresh `tags`. Read-only: the agent gets read and search tools only, so the arms stay comparable.
#
#   sh bench/run.sh [-n RUNS] [-t TASK] [-b BEFORE_REV] [-a AFTER_REV] [-m MODEL]
set -eu

runs=5
task=
before=c05dd6a
after=main
model=
while getopts n:t:b:a:m: opt; do
  case $opt in
    n) runs=$OPTARG ;;
    t) task=$OPTARG ;;
    b) before=$OPTARG ;;
    a) after=$OPTARG ;;
    m) model=$OPTARG ;;
    *) echo "usage: $0 [-n RUNS] [-t TASK] [-b BEFORE_REV] [-a AFTER_REV] [-m MODEL]" >&2; exit 2 ;;
  esac
done

repo=$(git rev-parse --show-toplevel)
bench=$repo/bench
work=${BENCH_WORKTREES:-$(dirname "$repo")/beltworks-bench}
out=$bench/out/$(date +%Y%m%d-%H%M%S)
mkdir -p "$work" "$out"

# Read and search only: no edits, no subagents (their transcripts are separate files), no web.
allowed='Read Grep Glob Bash(grep:*) Bash(rg:*) Bash(sed:*) Bash(head:*) Bash(tail:*) Bash(wc:*) Bash(cat:*) Bash(ls:*) Bash(find:*)'
denied='Edit Write NotebookEdit Agent Task WebFetch WebSearch'

prepare() { # arm rev
  dir=$work/$1
  if [ -d "$dir" ]; then
    git -C "$dir" checkout -q --detach "$2"
    git -C "$dir" reset -q --hard
  else
    git -C "$repo" worktree add -q --detach "$dir" "$2"
  fi
  git -C "$dir" submodule update --init --recursive -q
  # The benchmark itself must not be in either arm: its docs talk about ctags.
  rm -rf "$dir/bench"
  rm -f "$dir/tags"
  if [ "$1" = after ]; then
    (cd "$dir" && sh .claude/hooks/ctags.sh)
    [ -s "$dir/tags" ] || { echo "after arm: no tags built (is Universal Ctags installed?)" >&2; exit 1; }
  fi
  printf '%s\t%s\n' "$1" "$(git -C "$dir" rev-parse --short HEAD)" >> "$out/arms.tsv"
}

prepare before "$before"
prepare after "$after"
claude --version > "$out/claude-version.txt"
[ -n "$model" ] && echo "$model" > "$out/model.txt"

for t in "$bench"/tasks/*.txt; do
  name=$(basename "$t" .txt)
  [ -n "$task" ] && [ "$task" != "$name" ] && continue
  i=1
  while [ "$i" -le "$runs" ]; do
    # Alternate the order so drift over the session (cache, rate limits) hits both arms alike.
    if [ $((i % 2)) -eq 1 ]; then arms='before after'; else arms='after before'; fi
    for arm in $arms; do
      mkdir -p "$out/$arm"
      result=$out/$arm/$name-$i.json
      echo "$arm $name $i/$runs" >&2
      # set -f: the tool lists are split on spaces but their * must not glob.
      (set -f; cd "$work/$arm" && claude -p "$(cat "$t")" --output-format json \
          ${model:+--model "$model"} --strict-mcp-config \
          --allowedTools $allowed --disallowedTools $denied) > "$result" || echo "  run failed, kept $result" >&2
      sid=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("session_id",""))' "$result" 2>/dev/null || true)
      transcript=$(find "$HOME/.claude/projects" -name "$sid.jsonl" 2>/dev/null | head -1)
      [ -n "$sid" ] && [ -n "$transcript" ] && cp "$transcript" "$out/$arm/$name-$i.jsonl"
    done
    i=$((i + 1))
  done
done

echo "$out" >&2
python3 "$bench/report.py" "$out"
