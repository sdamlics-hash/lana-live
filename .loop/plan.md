# LANA 1039 avatar correction — 2026-10-24
Baseline: published and physically installed LANA 1038, stable commit d395346a95d91acd7d2f2f51a8cd8c37b8e81884. Preserve stable 1038 APK and installed tasks/memory.
Observed device issue: user rejected native chibi girl in Conversation tab as unattractive (screenshot 22:24). Not a transparency problem or ear-cropping bug.
Scope: replace LanaPresenceView with more mature adult face proportions and understated leafy accent; keep state-linked animation. Do NOT alter SpeechRecognizer, voice, server, signing, GOLD or KRAKEN. Static portrait artwork concept preview is illustrative only, not proof of installed appearance.
Acceptance: Android Kotlin tests and full build green, Android emulator memory smoke green, actual screenshot inspected at same 190dp portrait area and accepted visually; no regressions to controls/layout. User physical Samsung approval after explicit in-place update.
Rollback: keep 1038 tag and current installed app; do not uninstall or clear data. Correct issues in isolated PR; no direct release until review.
Cycle: plan -> implementation -> inspect screenshot and CI -> repair -> checkpoint; stop/replan after 5 unsuccessful cycles.
