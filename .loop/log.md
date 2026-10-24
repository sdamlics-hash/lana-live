# Validation log

- Added a Kotlin regression test before changing implementation: 4 tests ran,
  1 failed; minus was missing from the cleaned output.
- Mapped four arithmetic code points before decorative filtering.
- Kotlin: 22 tests passed across spoken text, phrase assembly and conversation
  runtime. An existing ambiguous return@ask label warning remains.
- Python speech suite: 10 tests passed, including Android source generation.
- These local tests call no external model or speech provider.
