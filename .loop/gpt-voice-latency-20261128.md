# GPT voice latency — second loop, 2026-11-28
Baseline cac3145009f8be3c8d9159edbfac0ccc06751569. No changes to stable release, keys, model, memory, or update channel.
Changes: make speech HTTP cancellable via the existing cancellation primitive; check cancellation at connection/upload/headers/read boundaries; disconnect off UI thread; stage MP3 on worker and retain a separate completion handler to delete stale files even after STOP; log elapsed time to file-ready and playback start without content or credentials.
Verification: 4 new speech HTTP regression tests are included by generator. Existing CI from the first loop was still running when this commit was prepared. New push triggers full Android tests and unsigned build. Real latency and audible speech on Samsung remain unmeasured. No streaming, voice substitution, or extra model/provider calls.
Next: inspect both CI results, compare foreground/background ownership, measure recognition→GPT→speech startup on Samsung before signing.
