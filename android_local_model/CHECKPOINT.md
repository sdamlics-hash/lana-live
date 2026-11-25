# 25 November 2026: local model next stage
Base commit: 7ffd169dd94a1b1101eeafba63d777ae3a7e6f8e; previous Android verification https://github.com/sdamlics-hash/lana-live/actions/runs/45580911333 passed build, unit tests, and no INTERNET permission. Package is ai.dreibus.lanalive.voicepreview, not the production package; no release.
Local independent JVM checks (Kotlin 1.9.0, fixtures only) 10/10 passed: valid, corrupt, truncated, too large, empty, cancelled, no overwrite, read failure, zero-progress stream, retry after error.
Importer SHA pin from the model repository. Model not bundled/downloaded. Actual model inference not tested.
Experimental runtime: LiteRT-LM 0.18.0, CPU, Qwen3-0.6B file, 614 MB per model card. Compatibility with existing Kotlin/Gradle is NOT assumed. CI resolves dependency before adding API probe; if incompatible, stop and inspect logs. Native model lifecycle/cancellation, UI import, memory integration and real device tests remain. No signed APK.
