# LANA batch self-review (2026-10-24)
Review type: **self-review**, not independent review.
- Baseline stable 361902655e881c6216514bb62a56bce6d2651416; released APK 1036 remains available.
- Diff inspected on PR #40: separate opaque mascot Paint avoids inheriting translucent halo alpha.
- LanaSpokenText maps the four arithmetic operators before removing decorative emoji, with JVM test.
- Coordinator accepts observed «Ладно ...» in addition to «Лана ...» and reads only actual saved non-archived team answers; tests cover positives, empty journal and unrelated request.
- CI Android build/unit/emulator: **pending at time of review**, do not infer success.
- Samsung acoustic audio, screen-off continuity and backend 32-second timeout are **not tested or solved by this patch**.
- Rollback: preserve release lana-stable-1036; do not uninstall/downgrade installed data; issue higher-version same-signature correction after diagnosis.
