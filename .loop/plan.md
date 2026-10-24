# SPEC: preserve arithmetic in spoken text

Baseline: 361902655e881c6216514bb62a56bce6d2651416.
Source: stable signing branch. Working branch: fix/lana-spoken-operators.

Problem: decorative-emoji filtering drops meaningful arithmetic signs.
Reproduction: clean("17 ➖ 25 = −8") returns "17 25 = −8".
Acceptance: preserve ➕ ➖ ✖ ➗ as + − × ÷, keep emoji filtering and original
history/display text, pass targeted and related tests, verify generated source.
Scope: voice text cleaning, regression test and development-loop instructions.
Rollback: revert the isolated commits. No production or device changes.
