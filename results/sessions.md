# Session Ledger

Record one row per dedicated session, from `/cost` at session end.
Environment: model = claude-fable-5 · Claude Code 2.1.206 · aiup-core 2.3.11 ·
aiup-vaadin-jooq 2.6.7 · allium plugin 3.7.0 · allium CLI 3.4.2 (language v3)

| Phase | Tool | Date | Input tokens | Output tokens | Cost (USD) | Wall-clock | Log file |
|-------|------|------|--------------|---------------|------------|------------|----------|
| 0 reference build | none (plain) | 2026-07-08 | 25k uncached + 489k cache-write + 14.8M cache-read | 316k | (add from /cost if wanted) | ~45 min | logs/phase0-reference.md — caveat: session also included the AIUP review discussion and evaluation design, so this overstates the build alone |
| 1 extraction | AIUP /reverse-engineer | 2026-07-08 | 6.7k uncached + 63.9k cache-write + 947.8k cache-read | 16.5k | $3.12 | 6m 4s (API 4m 22s) | logs/phase1-aiup.md |
| 1 extraction | Allium loop (distill + propagate + verify) | 2026-07-08 | 8.1k uncached + 202k cache-write + 5.4M cache-read (all models) | 60.9k (all models) | $11.35 | 21m 39s (API 15m 49s) | logs/phase1-allium.md — scope broader than AIUP's run: includes test generation + verification |
| 2 reconstruction | AIUP | 2026-07-08 | 14.5k uncached + 188.4k cache-write + 9.6M cache-read | 104k | $18.67 | 28m 39s (API 24m 24s) | logs/phase2-aiup.md — 2,923 lines added |
| 2 reconstruction | Allium | 2026-07-08 | 8.6k uncached + 339.5k cache-write + 6.4M cache-read (all models) | 109k (all models) | $16.80 | 34m 34s (API 23m 40s) | logs/phase2-allium.md — 2,876 lines added, converged tick 2, red-first |

Token measurement method: `/cost` at session end, or summed per-call `usage` fields
from the session transcript JSONL (`~/.claude/projects/<project>/<session>.jsonl`) —
the same source `/cost` reads. Use one method consistently so figures stay
comparable (phase 0 used the transcript method: 155 API calls at measurement time).

Caveats that apply to every row: figures include cache reads/writes and any
in-session tangents; ballpark by design.
