# Screen-off conversation ownership

The existing visible-screen microphone start and foreground-mode opt-in remain required. On Activity.onStop, an enabled foreground session transfers recognition to a service observer. The service routes wake/sleep phrases, local tasks and notes, tablet clock, capability answers, and ordinary dialogue. It saves each user phrase before dispatch and saves accepted answers before playback. Selected note consent is revalidated against the current workspace for each model request.

After READY or ERROR the service schedules a fresh recognition turn after 500 ms. ERROR never retries the previous model request. Recognition recovery remains bounded by the existing recognizer policy. A pure session gate invalidates delayed starts and late or duplicate final results. Returning to the screen transfers recognition back without cancelling a response already in progress. STOP invalidates the loop before cancellation; returning after STOP requires an explicit new start.

Specialist-team execution and coordinator commands still require the team screen when the Activity is detached; the service says so explicitly and never sends them as ordinary dialogue. This is a known remaining block, not full hands-free feature parity.

There is no automatic microphone restart after process death, force-stop, reboot, task removal or permission failure. No Android SpeechRecognizer implementation can establish a 24/7 guarantee from compilation. Physical Samsung checks remain required: lock during listening/thinking/speech; several successive replies; silence and network loss; STOP from notification; unlock without duplicate dispatch; force-stop/reopen task/note/history persistence and reset of note selection. Emulator smoke checks cover persistence, not physical audio/background behavior.


## Samsung 1024 observation and next candidate

The user observed no listening/reply with the screen off on Android 16 / SDK36. Diagnostics showed STOPPED, two recognition errors and code 11. This does not prove an OS kill: the old report merged actual Android error 11 and locally generated startup/final/turn watchdog errors. The next candidate records the origin, service foreground state, hands-free state, CPU wake-lock state and bounded lifecycle events without recording utterances or credentials. The last events remain available if the service disconnects.

A foreground service alone does not keep the CPU awake. The service now holds a non-reference-counted partial wake lock only while the explicit hands-free cycle owns recognition, with a 90-second system timeout renewed every 60 seconds. Every local stop, task removal, service destruction and transfer back to the screen releases it. This addresses a missing mechanism, not a proven Samsung root cause. Physical revalidation remains necessary. Android guidance: https://developer.android.com/develop/background-work/background-tasks/awake

Compound requests for the current date and location now answer the date from tablet clocks and explicitly say location is unavailable, without inventing coordinates. HTTP/authentication, DNS, TLS, timeout and response-format failures have distinct bounded user-facing messages. The avatar is now a native vector-style female portrait with emerald hair, a leaf crown and state-driven animation. Provider credentials and live configuration have not changed; this app does not attach to the current ChatGPT conversation.

## Conversation package after 1025

The Latin display name is LANA; the exact local wake acknowledgement is «Да, Сантик.». Both «Лана» and «LANA», plus the observed Samsung transcript «ладно ты тут», are local wake commands. Longer questions containing that phrase are not swallowed. Existing saved history is rendered with the Latin speaker prefix without rewriting stored text or changing package/preferences identifiers.

If the captured service configuration has an empty token, it can restore an existing encrypted credential only when the saved endpoint is exactly the same after trimming. It never moves credentials to another host or path, creates a key, or assumes a server is reachable. A truly missing credential or changed temporary tunnel still needs a real connection setup/check.

Ordinary request errors are announced once with system TTS, without another cloud/model request. A failed local announcement cannot recursively trigger announcements. Accepted model history remains separate from the local error announcement. JVM coverage includes ten consecutive accepted exchanges, duplicate callback rejection, a ledger recreation halfway through, final context restoration and bounded retained pairs. This is simulated transport/audio, not a Samsung ten-exchange acceptance test.
