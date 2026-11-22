# LANA checkpoint — Batch B1 / 2026-10-25
Stable release 1043 unchanged; branch work/lana-memory-v2-batch.
Added: LanaProjectMemory (bounded explicit facts per project, source, timestamp, fail-safe saves), AndroidKeystore encrypted AtomicFile, dual voice/typed routing, separate export/import UI with confirmation, settings and tests.
Next: open single draft PR; Kotlin Android CI + emulator persistence screenshot; fix failures up to 5 loops. Only after green checks and backup verification consider signed in-place candidate. User to perform actual Samsung tests. No VM/GOLD/Kraken/API keys altered.
Important: Claude, Manus and entire ChatGPT history NOT connected; 24/7 microphone stability not independently proven.


## Latest checkpoint — 2026-11-22 / B2 clear approved portrait
Current stable: signed LANA 1047 / release lana-stable-1047 / GitHub Actions run 44954656885 SUCCESS, but this only verifies build+unit checks, not Samsung speech.
Confirmed visual defect: android_team/assets/lana_approved_1047.jpg 144x144, scaled in LanaPresenceView. Exact higher-detail original found: personal Library "Futuristiska LANA AI portreta animācijā.gif", 506x508, 6 frames. First frame matches approved art. Exported locally, not yet present in GitHub.
Working branch: work/lana-1048-clear-portrait-20261122 from 745518f9. Signed release and installed app untouched.
Next: integrate recovered 506x508 still into this isolated branch replacing low-resolution drawable, test visual resource pipeline and Android CI. Only after build/cert/hash verification publish LANA 1048. Real Samsung 20-min conversation, STOP and note-consent persistence still need physical confirmation. Keep no new package ID, no uninstall/data reset, no credential changes.
