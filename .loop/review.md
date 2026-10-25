# LANA 1042 self-review only
- Speech assembler only deduplicates exact prefix/repeated finals or suffix-prefix overlap of at least 2 words; independent sentences are preserved. Atomic segment count/length rejection.
- Data privacy and correctness: memory write path returns explicit failure and in-memory copy rolls back; no false "запомнила" on failed disk save. New tests for retry, exception, incomplete local tasks.
- Code 11 evidence reports number of disconnects and successful phrases after them. Does NOT claim that the cause of code11 is fixed or Android SpeechRecognizer 24/7 works.
- Known concern: heuristic overlap reconstruction cannot reliably recover lost **first** word if Android never supplied it; independent Samsung test required.
- Verification currently pending: integrated Android Kotlin CI, emulator saved data, portrait screenshot, physical device/real API.
- Baseline 1041 preserved; no changes to GOLD/KRAKEN, no secrets.
