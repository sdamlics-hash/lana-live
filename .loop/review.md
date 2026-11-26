# Batch B1 self-review (not independent)
- Isolated project memory stored separately and encrypted at rest; no reuse of ChatGPT history or existing local notes; explicit consent required for fact creation.
- Data backup intentionally user-exported plaintext JSON and labeled as such; no secrets accepted in schema.
- All note/model paths unchanged; scoped commands return before network model dispatch. Existing 1043 signed package untouched.
- Regression scenarios specified: unrelated speech, command truncation, project A/B segregation, save fail/throw, duplicate facts, read after restart, invalid backup, confirmed restore.
- Risks: AndroidKeystore behavior must be checked on emulator and Samsung; no release based only on Kotlin compilation. No real device test yet.
- Independent review and physical persistence remain pending; CI results not yet known. After 5 cycles, rethink method.

1053 self-review: refresh on resume, preserve pending item if installed, read live selection and retain explicit confirmation. Playback remains unverified.

2026-11-26, самопроверка: в сгенерированном MainActivity проверены уникальный ID пробы, единственный путь к подтверждению через onDone, диагностика ошибок, таймаут и отмена при старте разговора/уходе с экрана. Android-сборку проверяет отдельный CI.

1057, самопроверка: APK сверяется с манифестом и установленным сертификатом. HTTP и чужие хосты отклоняются, включая перенаправления. Канал обновляется после обратного скачивания и сравнения APK; откат канала на меньшую версию отклоняется.

1059, самопроверка: тесты выбора охватывают сохранённый голос, подходящий системный, отсутствующие и сетевые голоса. Прослушивание на Samsung остаётся отдельным критерием.

1061, самопроверка: частичные распознавания и реплики с дополнительным запросом не получают быстрого пути. STOP остаётся отдельной веткой. Сокращение окна может изменить обработку пауз около одной секунды; реальный разговор на Samsung нужен отдельно.

1063, самопроверка: портрет загружается один раз. Анимация рисуется только в видимом окне, учитывает системное отключение и ограничивает частоту перерисовки. Влияние на время голосового ответа и картинку на Samsung нужно подтвердить отдельно.

1067, самопроверка: TTS onStart отражает сигнал Android о запуске, а не момент слышимости динамика. Нативный движок закрывается при ошибке, отмене, закрытии сервиса или после минуты простоя. Реального замера Samsung и проверки фонового диалога этой версии пока нет.
