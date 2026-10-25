# LANA 1042 — 2026-10-25: voice continuity, memory honesty, recovery evidence
Starting signed release: lana-stable-1041, commit 4a328243f5e33b01fe735d1b27ec37da152c1563. Preserve APK signing and Android saved data, no uninstall, no VM/trading changes.
Goal: three independent meaningful improvements in one batch:
1. Robust assembly of multiple final speech segments from Android, without repeating overlapping words/phrases in model requests. Do not drop unrelated or legitimately repeated words. Preserve >1400ms continuation behavior.
2. Incomplete or malformed local notes/tasks commands remain local and explicitly report NOT SAVED/NOT DONE rather than falling through to API as if an action happened. Keep existing memory schema and backup.
3. Diagnostic proof for Android recognizer disconnect code 11: counters for code11 events and actual recognized utterances after disconnect. Recovery code itself stays bounded; do not claim root cause solved.
Acceptance: Kotlin unit tests on normal, overlapping, missing command and storage errors; Android builds, existing tests all pass, emulator task/memory persistence, inspect screenshot, signing identity verified; no silent provider requests for incomplete local commands. Samsung verification: ten screen-off utterances and copy diagnostic, compare code11 counts and restoration.
Quality loop: plan with checks→implementation→run evidence→fix→record checkpoint; stop/replan after fifth round or repeated cause.
Rollback: original 1041 release retained, publish via same signed in-app update only after checks. Not validated: 24/7 Samsung, real Claude/Manus, live OpenAI API. Do not pretend otherwise.
