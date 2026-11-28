# GPT reliability candidate — 2026-11-28
Baseline: signed stable 1048 (d50453e8c4fbec634e16848e9c959e3bf2e1b9d0), preserved without changing release, update channel or installation.
Goal: retain cloud GPT and user data, improve cancellation and exactly-once response handling.
Changes: purge cancelled queued transport/audio work; reject reentrant duplicate final callbacks; do not overwrite accepted completion if an adapter throws after calling completion.
Tests: four new regression tests, full existing Android unit suite and unsigned debug build scheduled by isolated CI. No Samsung test or latency improvement claimed. No merge, signing, or publishing.
Next: inspect CI results and diagnose remaining live voice delay, then compare offline branch background/animation changes against stable GPT path; verify 20 min dialogue, STOP, persistence and same-signature update before release.
