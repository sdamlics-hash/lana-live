# LANA batch B1 — Project Memory v2 (2026-10-25)
Based on published stable LANA 1043; no server/VM/trading/credentials changed.
Build ONE integrated memory batch, no APK for isolated one-line fixes:
1. Structured, project-scoped explicit facts with ISO-UTC timestamps and source=user_explicit; private factual notes remain distinct from 8-turn chat history and ChatGPT history.
2. AndroidKeyStore AES-GCM encrypted atomic on-device storage in noBackup dir, bounded record counts, failure and tamper detected, no false success.
3. Voice **and typed** explicit commands: save for project, read project facts, list projects, search in project; never send the user's memory operations to the model. No automatic capture from ordinary chat.
4. User-initiated backup/restore (separate project facts only) with schema validation, confirmation and no silent overwrite; retain existing tasks/notes and backups.
5. CI regression tests (routing/false positives, project isolation, duplicate save, UTC timestamps, write failures, size rejection and restores), Android emulator persistence with screenshot. Never consider success based on a green compile alone.
6. Truthful UI: clearly state independent encrypted local project memory; Claude/Manus/ChatGPT history remain unconnected.

Tests: Kotlin JVM + Android emulator persisted-record verification; no SECRET in artifact, no external model calls. For physical acceptance: Samsung 1043 remains until an integrated candidate is verified; 10 voice commands and reboot/persistence plus backup/restore, without uninstall. Do not merge/publish with red CI. No production service changes. After five failed repair loops or same cause repeated, stop and revisit architecture.
Rollback: tag lana-stable-1043 unchanged, same signing identity and package; keep backup before dangerous restoration; reinstall/downgrade forbidden as potential data loss.


## B2 — LANA 1048 portrait clarity + voice acceptance (2026-11-22)
Baseline: signed LANA 1047 published 2026-11-21, SHA 745518f9; never overwrite/delete installed 1047 or signing material. Isolated branch: work/lana-1048-clear-portrait-20261122.
Reproduced flaw: shipped drawable android_team/assets/lana_approved_1047.jpg is 144x144 pixels (3,122 bytes); BitmapFactory scales it into a much larger Canvas. User observed blur.
Source recovery: user's own Library file "Futuristiska LANA AI portreta animācijā.gif" includes the approved art with six frames, each 506x508. First frame matches approved LANA AI robot portrait; a static 506x508 candidate has been extracted locally.
Acceptance: replace 144px drawable with a >=500px version of the SAME approved portrait, no new identity; verify source dimensions, resource wiring, Android unit tests/build, package/signing/version metadata and release asset hash. Do not call a build a device pass. Independent Samsung acceptance: 20 minutes of real speech, ten or more consecutive turns, STOP interrupts reliably, no duplicate turns, memory checkbox selection survives screen reopen and process restart.
Blockers: high-resolution binary not yet committed to GitHub, so NO 1048 APK or physical voice claim. No changes to server, GOLD/KRAKEN, secrets, or REAL. Rollback: keep signed release 1047 intact; new build only after checks pass and certificate matches prior signed package.

## 1053 — offline speech settings recovery
Baseline 82c7b96, Samsung 1051 transcribed phrases and produced text but no confirmed audio.
Check that returning from Android settings refreshes installed voices, preview uses the current picker item, and approval remains explicit. Rollback for source is the baseline ref. Device acceptance is audible Russian replies including 25 turns and STOP.

## 2026-11-26 — подтверждение пробы офлайн-голоса
База e07bccc, на Samsung видна 1051. В 1053 кнопка сохранения доступна сразу после постановки пробы в очередь.
Критерий: Android завершает именно текущую пробу до предложения сохранить голос. Ошибка, таймаут, уход с экрана и устаревший callback не дают сохранить результат. Приёмка на Samsung: слышимая проба, русский ответ, STOP и серия реплик в авиарежиме.
Откат исходников — e07bccc. Установленную сборку и подпись сохраняем.

## 1057 — обновление нажатием одной кнопки
База 804f132. Критерии: по кнопке проверяется выделенный канал, APK сверяется по размеру, SHA-256, пакету, версии и прежней подписи; Android запрашивает установку. Ручной выбор доступен долгим нажатием. Сетевые настройки разговора и отправка экрана отключены. Сборка проверяется CI; физический тест остаётся отдельным. Откат 1058 подписывается прежним сертификатом.

## 1059 — голос после обновления без дополнительного подтверждения
База 17e8978. На Samsung 1057 проба слышна, разговор приходит текстом, журнал требует подтверждения голоса. При отсутствии сохранённого выбора использовать установленный русский офлайн-голос. Успешная проба с явно выбранным голосом сохраняет его сразу. Тесты проверяют приоритет сохранённого голоса и исключение сетевых голосов. Подписанный откат — 1060 на исходники 1057.

## 1061 — задержка разговора, Q200
1059 подтверждена пользователем на Samsung. Журнал показывает 1411 мс на одном ответе и 10 секунд до отказа на следующем фрагменте. Критерии: однозначный вызов имени и проверка связи идут сразу после финала Android; обычные вопросы ждут 900 мс, очевидно незаконченные сохраняют 1400 мс. Частичный текст не отправляется, STOP не ждёт. Q200 — 200 сценариев JVM по таймерам, объединению, отмене и длинным репликам. Откат 1062 к 1059.

## 1063 — портрет и звёзды
База 9614973, на Samsung 1061. Показать утверждённый портрет 506×508 вместо шара. Девять звёзд окружают портрет, сближаются при подготовке голоса и ответе. Шаг отрисовки не чаще 30 кадров/с, анимация отключается при STOP, скрытии окна и системном отключении анимаций. Приёмка: тесты геометрии, генерация с существующим ресурсом, подпись APK и канал. Откат 1064 на код 1061.


## Samsung 1063 — восстановление разговора
Основа 1063. Проверить повторное открытие разговора после сворачивания, включая исчезновение активного распознавания. Временная потеря экрана освобождает микрофон. Явный STOP и завершение сеанса по-прежнему требуют ручного старта. Откат — ветка выпуска 1063.

## 1065 — разговор при переключении приложений
База — исправление восстановления окна поверх 1063. Старт по явной кнопке «Слушать» использует фоновый сервис по умолчанию, сохранённый отказ учитывается. При отклонении запуска сервиса разговор в видимом окне продолжается. Приёмка: сборка, подпись, доступность канала, разговор на Samsung при переходе в Google и проверка STOP. Откат 1066 — код 1063.

## 1067 — пакет улучшений разговора
База 1065, фон подтверждён на Samsung. Критерии: быстрые локальные ответы на обращение, обратную связь и часы; служебный текст и пересказ вопроса отсекаются; повторный запрос использует прогретый движок до 60 секунд с отдельной сессией; журнал показывает ответ и старт системной озвучки отдельно. Сто программных прогонов, тесты, подпись и публикация. Откат 1068 возвращает 1065.

## 1069 — расширяемая сцена и быстрые реплики
База 1067. Добавить кнопку расширения сцены, три вида звёзд и разные траектории с сохранением поля вокруг лица. Ускорить точно распознанные вопросы о времени и проверку связи; фразы с дополнительным вопросом ждут продолжения. Проверить геометрию в разделённом экране, голосовые регрессии и подписанный канал. Откат 1070 к 1067.

## Качество свободного разговора после 1069
База 9e82f69. Скрин Samsung: модель убрала первое вопросительное слово и повторила остаток; комментарий о повторе снова вернул шаблон. Критерии: подобные эхо-ответы отсекаются; вопрос про возможности на планшете получает конкретный локальный ответ; нормальные короткие ответы проходят. Откат — 1069. Ветка для проверок перед следующим пакетом.

## Свободный диалог с ролями
База eb8f681, опубликована 1069. Передавать краткую историю как пары user/assistant и новый вопрос отдельным сообщением. Поддержать существующую историю и ограничить контекст двумя парами. Приёмка: сто сценариев границ и неподдерживаемых записей, Android unit-тесты, запуск модели на изолированном эмуляторе и проверка вопроса по контексту. До реальной проверки канал не менять.

## Следующий пакет после 1071
Запрос пользователя: 100 улучшений и 100 циклов до предложения обновления. Считать отдельно функциональные изменения, циклы доработок и тестовые сценарии. Первая работа: обрабатывать законченные служебные блоки Qwen, различать сбой декодирования и отсутствие ответа, защищать границы UTF-16 при обрезке ответа и истории. Критерии: программная регрессия и реальный Android-прогон модели; действующий канал остаётся 1071.

## Задержка одной голосовой реплики
База 540b5d4. Привязать конец текущей распознанной реплики к началу TTS в открытом окне и фоне. Текстовый ввод, повтор, остановка и поздний callback не должны унаследовать чужой замер. Android onStart — граница запуска синтеза, скорость динамика проверяется на устройстве. Канал 1071, выпуска пока нет.

## Сравнение инструкций Qwen
База 3816eb3. Причина смены подхода: предыдущая проверка знаний дала неожиданный ответ на вопрос о столице Франции. Сравнить текущую и короткую инструкции с тем же движком на пяти синтетических вопросах, включая старые проверки арифметики и контекста. Записать полный статус каждой попытки независимо от провалов соседних. В экспериментальном журнале допускается только вывод на фиксированные синтетические вопросы. Изменение в рабочей инструкции и публикация требуют устойчивых результатов.
