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

## B2 continuation — 2026-11-22 / source integrated
Recovered full-size first GIF frame was encoded to 506x508 JPEG (SHA-256 d49b2e74abd37ca42869592910e0d9164d9ab14d0d8065b3976d39df3cac552e) and uploaded as Git blob 721f0942a52689353e9bbfd1bcda647d0b8b38a9; verified byte-for-byte by matching local Git blob SHA. Committed to isolated branch at 8668259d70a2a4b1155277777a14dc4fd57a71b1, replacing 144x144 resource. Draft PR #67 compares to existing signed 1047 build branch; stable and device unchanged. CI: image dimension/hash check and exact generated drawable copy passed in run 45003835541; full Gradle and other PR checks remain IN_PROGRESS as last checked. Safe backup ref backup/lana-stable-1047-20261122 preserves baseline. Next: review CI conclusions, fix failures if any, and ONLY THEN consider signed 1048 candidate; 20-minute real Samsung acceptance remains outstanding. Do not report 1048 APK ready before evidence.
