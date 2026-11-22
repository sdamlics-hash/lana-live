# LANA Library — ingestion contract (#68)
Status: draft specification, not implemented or released.

## Goal
Assign five or more books; ingest all extractable text, preserve original provenance, answer questions across books; optional read-aloud. This is retrieval, not training model weights.

## Inputs and completeness
TXT: strict UTF-8; fail visibly on decode errors.
EPUB: enumerate every spine document in reading order, preserve chapter and position, report DRM/parse failures.
PDF: enumerate every page; image-only pages require separate OCR and must not be marked complete.
Store source SHA-256, parser version, expected and processed section counts, failures, character count, per-section offsets.
Statuses: QUEUED, PROCESSING, PARTIAL, COMPLETE, FAILED, CANCELLED. Never COMPLETE with missing or unreadable sections.

## Recovery and safety
Persist progress transactionally after each section; resume after process death without duplicate chunks.
Delete source and all derived chunks, summaries and embeddings on user deletion.
No whole-book cloud uploads without permission. Enforce storage, tokens and spending limits.

## Answers and voice
Retrieve original passages with title, chapter/page and offset; separate direct quotation from interpretation; never invent citations.
Compare across five books and explain uncertainty. Respect copyright and permissions.
Optional TTS supports bookmark, pause/resume and STOP invalidating stale replies.

## Acceptance gates
1. Five fixtures: all sections, including final chapters, indexed and hashes checked.
2. Corrupt EPUB and image-only PDF cannot falsely report COMPLETE.
3. Kill/restart midway: exact resume without duplication.
4. Twenty questions across beginning, middle and end with verified provenance.
5. Delete/reimport, consent revocation, and cloud permission tested.
6. STOP, offline behavior, memory/storage and API-cost ceilings tested.

## Priority and checkpoint — 2026-11-22
Existing Android voice 20+ minute Samsung acceptance, STOP, memory and signed update identity take precedence.
Plan: specified. Implementation: pending. Tests: pending. No APK installation or merge.
Next: add fixture-driven parser tests and implement one format at a time in isolated draft PR.
Rollback: abandon draft branch; installed LANA 1047 unchanged.