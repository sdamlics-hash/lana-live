# LANA batch B1 — Project Memory v2 (2026-10-25)
Based on published stable LANA 1043; no server/VM/trading/credentials changed.
Build ONE integrated memory batch, no APK for isolated one-line fixes:
1. Structured, project-scoped explicit facts with ISO-UTC timestamps and source=user_explicit; private factual notes remain distinct from 8-turn chat history and ChatGPT history.
2. AndroidKeyStore AES-GCM encrypted atomic on-device storage in noBackup dir, bounded record counts, failure and tamper detected, no false success.
3. Voice **and typed** explicit commands: save for project, read project facts, list projects, search in project; never send the user's memory operations to the model. No automatic capture from ordinary chat.
4. User-initiated backup/restore (separate project facts only) with schema validation, confirmation and no silent overwrite; retain existing tasks/notes and backups.
5. CI regression tests (routing/false positives, project isolation, duplicate save, UTC timestamps, write failures, size rejection and restores), Android emulator persistence with screenshot. Never consider success based on a green compile alone.
6. Truthful UI: clearly state independent encrypted local project memory; Claude/Manus/ChatGPT history remain unconnected.

Tests: Kotlin JVM + Android emulator persisted-record verification; no SECRET in artifact, no external model calls. For physical acceptance: Samsung 1043 remains until an integrated candidate is verified; 10 voice commands and reboot/persistence plus backup/restore, without uninstall. Do not merge/publish with red CI. No production service changes. After five failed repair loops or same cause repeated, stop and revisit architecture.
Rollback: tag lana-stable-1043 unchanged, same signing identity and package; keep backup before dangerous restoration; reinstall/downgrade forbidden as potential data loss.
