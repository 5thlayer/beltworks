# Code navigation: the ctags symbol index

Don't read whole files to find code. Search `tags`, a Universal Ctags index of `src/main/java`, then read only the lines you need.

## Setup

The `.claude/hooks/ctags.sh` SessionStart hook rebuilds `tags` every session, and in a cloud session installs Universal Ctags first. Elsewhere, install it yourself (`ctags --version` must say "Universal Ctags"; Exuberant Ctags lacks the options below).

Rebuild it after adding, renaming or moving classes or methods, or after a big refactor:

```sh
ctags -R --languages=Java --fields=+nKSl --extras=+q -f tags src/main/java
```

`tags` is gitignored. It covers `src/main` only: search `src/test` and `src/gametest` with `grep`.

## Usage

Each line is `name<TAB>path<TAB>/^pattern$/;"<TAB>kind<TAB>line:N<TAB>language:Java<TAB>class:Owner<TAB>signature:(...)`. `line:N` is the line number; `class:` names the enclosing type, nested types dotted (`BeltContents.Entry`).

- Where a symbol is defined: `grep -P '^BeltContents\t' tags`. Qualified names work too: `grep -P '^BeltContents\.Added\t' tags`.
- A class's members: `grep -P '\tclass:BeltContents(\t|$)' tags`. Without the `(\t|$)`, the pattern also matches `BeltContentsFoo` and nested types' members.
- Read only what you need: take `line:N` and read 40 to 80 lines around it, not the whole file.
- References: ctags has none, so `grep -rn 'SymbolName' src/main/java`.
- Mixins: search for the target string. Injection targets (`method = "..."`, `@At(target = "...")`) are strings, not references.

## Rules

- Read a whole file only if it's under about 300 lines or you're editing most of it.
- Rebuild `tags` after adding, renaming or moving classes or methods: a stale `line:N` points at the wrong code.
