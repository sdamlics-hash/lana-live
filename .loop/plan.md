# LANA 1040 batch plan — 2026-10-25
Baseline: signed LANA 1039 commit 7a0e22631b8d6be9c735a6b5fa3699c1a39515a2, immutable release lana-stable-1039. User has previously installed 1038; on-device 1039 is NOT independently confirmed.
Goal: bring LANA closer to the desired voice-led assistant, with one combined upgrade, not serial cosmetic hotfixes.
Changes:
1. Preserve adult 1039 portrait and draw a distinct animated botanical LANA emblem on top, fully visible at small Samsung screen sizes; graceful portrait fallback. No return to fox or childish face.
2. Explicit team status for OpenAI, Claude and Manus in authenticated read-only diagnostics. Missing server fields remain unverified, not falsely marked connected. No keys or user messages in diagnostics; no external model calls.
3. Refresh LANA development prompts to reflect user-approved loop: plan with criteria, execution, evidence from tests, findings, rework, checkpoint; distinguish self-check from independent device verification; never claim background work or ChatGPT history sync.
4. Regression tests for provider status compatibility, backend diagnostics, dialogue/team constraints, and Android UI build / emulator preview/persistence.
Acceptance: Kotlin Android checks green, Python server diagnostics tests green, emulator tasks/memory and screenshot verified, signed update only after passes, no downgrade/uninstall/clearing data. After Samsung installation, verify logo proportions and states, API check in settings, and 10 screen-off voice turns. OpenAI model connection can be confirmed ONLY by fresh successful real transport request; health check alone is not proof.
Rollback: 1039 untouched while candidate develops; previous published release remains available; if installed issue, correct forward with same signing identity rather than uninstalling / forced downgrade.
Stop/re-plan after five repair rounds or repetition of unresolved cause. No changes to GOLD/KRAKEN, production servers, secrets, or unrelated workflows.
