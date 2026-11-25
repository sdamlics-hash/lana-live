# LANA 1048 — offline candidate
Base: d50453e8c4fbec634e16848e9c959e3bf2e1b9d0; existing release/tag untouched.
Plan: local foreground and detached dialogue; retain workspace, history, STOP and existing settings; refuse cloud team; enforce on-device speech and non-network Russian TTS; deny INTERNET in merged manifest.
Ten Kotlin test groups plus Android regression tests and final APK manifest inspection are defined. CI must actually finish before any claims of passing. No signed update or release in this branch.
Limits: local model absent; free-form dialogue not supported, local speech depends on installed Android components. UI still uses old avatar; connection settings not yet redesigned. Verify actual 20-minute airplane-mode session and update compatibility before release.
Rollback: discard this isolated branch; installed 1048 not modified.
