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

1069, самопроверка: расширение ограничено размерами экрана, быстрый режим включается только для известных полных фраз. При добавлении слов или цифр остаётся обычное окно ожидания. Вид на планшете и скорость озвучки проверяются отдельно.

Второй цикл: CI 45815624797 и 45816602700. Компиляция и модульные тесты тайминга прошли. Повтор с диагностикой подтвердил арифметику и вопрос по контексту, но третий свободный вопрос о столице Франции получил unexpected_answer. Этот сценарий сохраняем. Публикации нет, 1071 остаётся действующей. Дальше вместо повторного запуска той же конфигурации пересмотреть параметры и качество ответов компактного Qwen на отдельном наборе открытых вопросов.

CI 45818783058: завершены десять реальных ответов Qwen Android, по пять на инструкцию. Результат: 3/5 у обеих. Текущая инструкция назвала правильную столицу Франции латиницей (Paris), короткая дала неверный город. Для Германии обе ошиблись (Гамбург / Мюнхен). Арифметика, контекст и компас прошли у обеих. Один прогон на пару не подтверждает устойчивость. Короткую инструкцию оставляем только в тесте. Следующий шаг: проверить более сильную локальную модель на тех же и новых вопросах, отдельно замерить скорость и память. Канал 1071.
