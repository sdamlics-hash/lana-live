# LANA 1043 — accurate recent-memory recall (2026-10-25)
Baseline: signed 1042, branch build/lana-stable-first-signing. Preserve user app/data; no VM or trading changes.
Observed on physical Samsung screenshot 08:02: when asked "а что ты помнишь последние вчерашние разговоры наши", LANA falsely answered "прямо сейчас я не сохраняю наши разговоры" despite bounded encrypted local dialogue storage and on-device transcript.
Batch:
1. Direct, no-model read-only answer from the *actual local encrypted recent dialogue ledger* for recognized "что ты помнишь о вчерашних/предыдущих разговорах" requests. Only list grounded recent user turns if present, no invented dates/topics, no API call, no data mutation.
2. Honest mode/availability boundary: legacy client may store up to eight recent pairs (sends newest six), but no timestamps or full ChatGPT history. Non-legacy requests must not claim the same local history is available; if unreadable report uncertainty rather than fabricate.
3. Compact system prompt acknowledges scoped device context and must not assert memory never exists. Keep question max 1800, combined request <=2400, existing style precedence.
Acceptance: local-memory unit tests for exact request from device, empty ledger, scoped/unreadable history, nonlegacy mode, timestamp uncertainty and no accidental API dispatch; Kotlin Android CI/emulator persistence and screenshot, signed in-place update only if passing. Samsung must verify content of actual last replies; screenshots alone don't prove persisted ciphertext.
Rollback: signed release 1042 remains; do not delete current app/data. No model or credentials forwarded when inspecting local memory. Distinguish self-review from independent device verification; stop/replan after five cycles.
