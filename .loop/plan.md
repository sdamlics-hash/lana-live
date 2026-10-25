# LANA 1041 coordinated improvements — 2026-10-25
Baseline: signed stable 1040, release lana-stable-1040, commit 717eb2340777c38d6929f6dfa4f06a16b8d05d46. Existing installed build not inferred from GitHub; no uninstall or data changes.
User objective: evolving LANA as one Android app closer to a powerful, truthful voice assistant with seamless repeat turns, background listening, memory, coordinated AI team and live companion.
Batch:
1. Fix stale voice capability statement: Samsung has already confirmed several successive screen-off turns, but not 24/7. Don't claim fully stable service.
2. Guard explicit spoken assignments to Claude/Manus while unconnected, with clear feedback and no OpenAI provider request or phantom task; retain explicit OpenAI team task dispatch and ChatGPT-history boundary.
3. Improve companion voice-phase visual expression with listening/speaking indicators, preserve current adult portrait and green leaf logo.
4. Add unit regression tests for truthful provider commands, voice state visuals via build/emulator snapshot, dialogue and saved data.
Acceptance before merge: core+Android+voice CI green, emulator persistence screenshot inspected, no unintentional provider dispatch, original 1040 published APK intact. On Samsung after update user verifies voice loops with screen off, logo display and provider boundary. Android code 11 still requires independent physical confirmation; do not invent a fix.
Loop: plan→implement→run factual checks→repair issues (max 5 rounds)→checkpoint.
Rollback: signed 1040 tagged and untouched; same signing identity, no app removal/downgrade; forward correction if necessary. Never change server or GOLD/KRAKEN without separate authorization.
