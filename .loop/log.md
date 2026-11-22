# Batch B1 implementation log — 2026-10-25
Baseline: signed release 1043, unchanged. Goal: no more single-line cosmetic releases.
Implemented as one cohesive batch:
- New project-scoped facts, explicit voice commands with timestamps and source=user_explicit; no automatic learning from chat. Up to 100 bounded facts, per-project search and summaries, deduplication.
- AndroidKeyStore AES-GCM encrypted, AtomicFile noBackup storage with singleton serialized read/modify/write. On failed commit state rolls back; corrupt or unreadable data gives explicit error rather than invented recall.
- Routed BOTH background speech (service) and foreground/typed askFromLana through local handler before any model dispatch; incomplete commands rejected locally.
- Added separate versioned user-initiated JSON backup and explicit-confirmation restore, clearly labeling export as UNENCRYPTED. Existing tasks/notes backup and 8-pair dialogue store unchanged.
- UI typed shortcuts and settings explanation; standalone JVM tests of isolation, time, duplicates, write failures, invalid formats, imported backups and incomplete utterances.
PENDING: Android CI and real Samsung storage/voice acceptance. No VM, Claude/Manus, GOLD or Kraken changes.


## B2 log — 2026-11-22
- Queried actual GitHub ref 745518f9 and verified Actions run 44954656885: success, run_number=47, signed release tag lana-stable-1047, APK 14,162,853 bytes.
- Parsed shipped JPEG header from GitHub contents (not inferred from screenshot): 144x144, 3,122 bytes. Found scaling path LanaPresenceView.drawAndroid via BitmapFactory + canvas.drawBitmap.
- Recovered exact approved portrait from personal Library GIF (506x508, 6 frames), visually checked first-frame match, exported a static higher-resolution image. Source file remains in user Library.
- Created isolated working branch work/lana-1048-clear-portrait-20261122. No binary uploaded to GitHub and no new APK built; all installation and 20-minute voice claims pending.
