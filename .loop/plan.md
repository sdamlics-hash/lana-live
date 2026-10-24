# LANA combined release after installed 1036
Baseline stable commit: 361902655e881c6216514bb62a56bce6d2651416; known-good release lana-stable-1036.
Observed Samsung: «Ладно Прочитай последний ответ команды» was routed to a generic question; stored team answer not spoken. Mascot near-invisible; math operator speech drops symbols.
Scope: only Android voice command normalizer/tests, fox bitmap paint, speech math, plus development-loop documentation. Do not touch VM, credentials, GOLD/Kraken or installed data.
Acceptance: Kotlin/Android suite passes; command variants route locally and return latest non-archived journal answer (or honest absence), without calling model; fox uses opaque bitmap paint; spoken math preserves operators. APK signed with existing key, same package, version >1036; publish only after CI. Actual Samsung audio/listening requires user verification.
Rollback: do not install candidate if tests fail; preserve release lana-stable-1036 and baseline branch commit. For a bad installed update, do not downgrade/clear data blindly; diagnose and publish a higher-version corrective APK with same signing identity.
