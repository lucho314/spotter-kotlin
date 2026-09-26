# Spoter Kotlin

## Development agents pipeline

Four project subagents live in `.Codex/agents/`, all with effort `high`:

| Agent | Model | Role |
|---|---|---|
| `dev-planner` | Opus 5.5 | Explores code, writes the implementation plan. Read-only. |
| `dev-implementer` | Sonnet 5 | Implements the plan; fixes reviewer issues. |
| `dev-reviewer` | Opus 5.5 | Reviews changes: objectives, fault tolerance, clarity/modularity, interfaces, tests. Returns verdict + bugs. |
| `dev-documenter` | Haiku 4.5 | Documents the project: purpose, structure, architecture, components, changelog (`README.md`, `docs/`). |

**Trigger rule:** use this pipeline ONLY when the user explicitly says to use the development agents ("usar los agentes de desarrollo" or similar). Otherwise, do the task directly with the currently selected model — do not spawn these agents.

**Flow when triggered:**
1. Spawn `dev-planner` with the full task and relevant context. Show the user a summary of the plan.
2. Spawn `dev-implementer` with the complete plan (it has no access to this conversation).
3. Spawn `dev-reviewer` with the plan + the implementer's report.
4. If verdict is `CAMBIOS REQUERIDOS`: send the issues to `dev-implementer` (continue it via SendMessage), then review again. Max 3 review cycles; if still failing, stop and report to the user (skip documentation).
5. When the reviewer returns `APROBADO`: spawn `dev-documenter` with the plan, the implementer's report and the reviewer's verdict.
6. Report the final result to the user: changes, test outcome, reviewer verdict, docs updated.
