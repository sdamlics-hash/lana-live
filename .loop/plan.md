# LANA next combined candidate — 2026-10-24
Baseline: stable release 1037, commit 470f684518534d2b42d92c33a3e1ea54110c4ad3.
Reported real Samsung behavior: screen-off replies succeed repeatedly; recognition service returns Android code 11 after several turns; partial recognized fragment "плюс пять" lacks first operand. Current app should NOT be called broken 24/7 solely for code 11; recovery observed. User manually stops the session.

Scope: improve guarded SpeechRecognizer lifecycle after onResults, reject clearly truncated stand-alone math fragments locally before model request, add privacy-safe diagnostics/tests, and replace fox with NEW expressive original leafy-girl companion rendered natively (no external PNG dependencies). Preserve installed package, voice transport, local tasks/memory, signing identity and update button.

Acceptance before merge: Kotlin unit tests for split math, legitimate math, session/retry behavior; integrated Android build and emulator memory persistence; on emulator full face+body visible above halo; screenshot evidence; no production changes before successful combined checks.
Physical Samsung acceptance after separate deployment: >=10 complete screen-off questions, no missed first operand, correct voice/no double-submit, recovery from code11 without user interaction; no loss of tasks/memory after in-place update.
Rollback: unchanged deployed 1037 and GitHub tag lana-stable-1037; PR isolated. No APK deletion/downgrade. If bad update installed, prepare a higher-version same-signature corrective release; do not clear data.
Stop and replan after five review cycles or repeated unresolved failure.
