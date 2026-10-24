# LANA development loop

1. SPEC: save one task, baseline commit, reproducible problem, acceptance criteria,
   scope and rollback in `plan.md`.
2. BUILD: use an isolated branch; record commands, changes and evidence in `log.md`.
3. REVIEW: inspect the diff, run relevant checks, verify observable behaviour and
   record findings in `review.md`. Label self-review honestly. Independent review
   requires a separate reviewer; do not claim it without evidence.
4. REPAIR: fix confirmed findings and rerun affected checks. If the plan is wrong,
   return to SPEC. Stop after five review rounds or when the same unresolved cause
   recurs a second time; revise the approach.
5. CHECKPOINT: save the task, commit/diff, results, limits and next action in
   `checkpoint.md`.

If access, dependencies or reproduction are missing, record the blocker. A test
that did not run is not a pass. Compilation does not establish voice reliability.
Before dangerous changes, verify a backup and a concrete rollback. Respect the
user's current scope and authorization.

The first code pilot runs locally without external model calls, production
changes, APK installation, service restarts, deletion or bulk cleanup. This file
does not install a plugin or create background execution.
