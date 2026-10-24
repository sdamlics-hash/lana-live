# Work log — 2026-10-24
- Confirmed live Samsung 1037: screen-off conversation replied to 12+8=20, 13-4=9, 128+28=156, tablet time; user pressed manual stop. Recognition backend code 11 recurring, not a verified permanent session crash.
- Baseline stable signed 1037 commit 470f684518534d2b42d92c33a3e1ea54110c4ad3 kept unchanged.
- Created isolated branch work/lana-screenoff-companion-next.
- Source inspection: android_team/src/LanaServiceRecognizer.kt cancels a recognizer after its onResults even though callback is final, then restarts after 100 ms. Hypothesis of cancellation race; cannot establish Android backend root cause from code alone.
- Changed onResults path to avoid redundant cancel and use 300ms guarded continuation restart; code 11 still recreates recognizer with bounded backoff. No model request retry.
- Added pure classifier LanaIncompleteSpeech for unmistakable dangling math fragment 'плюс пять' or equivalents; rejects before model dispatch and asks for full repetition via existing local voice feedback.
- Added JVM tests for the filter and registered it with Android generator.
- Replaced old fox bitmap rendering with wholly new native forest muse character; accents and motion tied to real speech states. No existing image assets altered.
- Manual Android/emulator tests and physical Samsung verification not yet complete.
