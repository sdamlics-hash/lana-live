# Batch B1 implementation log — 2026-10-25
Baseline: signed release 1043, unchanged. Goal: no more single-line cosmetic releases.
Implemented as one cohesive batch:
- New project-scoped facts, explicit voice commands with timestamps and source=user_explicit; no automatic learning from chat. Up to 100 bounded facts, per-project search and summaries, deduplication.
- AndroidKeyStore AES-GCM encrypted, AtomicFile noBackup storage with singleton serialized read/modify/write. On failed commit state rolls back; corrupt or unreadable data gives explicit error rather than invented recall.
- Routed BOTH background speech (service) and foreground/typed askFromLana through local handler before any model dispatch; incomplete commands rejected locally.
- Added separate versioned user-initiated JSON backup and explicit-confirmation restore, clearly labeling export as UNENCRYPTED. Existing tasks/notes backup and 8-pair dialogue store unchanged.
- UI typed shortcuts and settings explanation; standalone JVM tests of isolation, time, duplicates, write failures, invalid formats, imported backups and incomplete utterances.
PENDING: Android CI and real Samsung storage/voice acceptance. No VM, Claude/Manus, GOLD or Kraken changes.


## B2 log — 2026-11-22
- Queried actual GitHub ref 745518f9 and verified Actions run 44954656885: success, run_number=47, signed release tag lana-stable-1047, APK 14,162,853 bytes.
- Parsed shipped JPEG header from GitHub contents (not inferred from screenshot): 144x144, 3,122 bytes. Found scaling path LanaPresenceView.drawAndroid via BitmapFactory + canvas.drawBitmap.
- Recovered exact approved portrait from personal Library GIF (506x508, 6 frames), visually checked first-frame match, exported a static higher-resolution image. Source file remains in user Library.
- Created isolated working branch work/lana-1048-clear-portrait-20261122. No binary uploaded to GitHub and no new APK built; all installation and 20-minute voice claims pending.

## B2 implementation continuation — 2026-11-22
- File bridge: exported 506x508 first-frame JPEG, SHA-256 d49b2e74abd37ca42869592910e0d9164d9ab14d0d8065b3976d39df3cac552e; Git create_blob returned SHA 721f0942a52689353e9bbfd1bcda647d0b8b38a9 matching local git hash-object. Temporary transfer text moved to Library trash.
- Branch commit 8668259 changed only image blob; additional commits added a dedicated CI workflow and pull request #67 (draft). PR compare verified five changed paths only (portrait, workflow, three loop docs). Backup branch backup/lana-stable-1047-20261122 created.
- In GitHub Actions run 45003835541 portrait source/header hash and generated app asset copy passed; full Gradle test/build is not yet complete. Also LANA Voice Repair and core package validation checks in progress. No release/installation performed.

1053: generated same-package Android project and checked four code assertions and the manifest removal marker for INTERNET. No local Gradle/device run.

2026-11-26: получен CI-архив 1053/1054 (run 45634888320), обе SHA-256 сверены. В 1055 подтверждение пробы привязано к onDone конкретной реплики. Повторная генерация в занятую папку штатно отклонена, чистая генерация прошла. Приёмки на планшете пока нет.

1057: добавлен выделенный канал обновлений с ручным резервным путём. Генератор отключает старые сетевые кнопки и удаляет сервис захвата экрана. Обычные голосовые и текстовые маршруты остаются локальными. CI собирает, проверяет подписи и публикует только проверенный APK в выделенном канале.

1059: в чистой генерации успешно собраны исходники Android с выбором локального голоса. При сохранённом выборе применяется он, иначе доступный системный голос. Подписанная сборка и публикация проверяются в CI.

1061: быстрый путь для известных реплик только по финалу Android и при пустом буфере. Окно обычных фраз 900 мс, явно незаконченных 1400 мс. Q200 собран из пяти классов сценариев по 40 вариантов.

1063: встроен утверждённый портрет 506×508; побайтовое сравнение ресурса с исходником прошло. Добавлены тесты сближения звёзд и отступов для трёх размеров, включая узкий экран.

1067: локальный маршрут дополнен быстрыми ответами, фильтром эхоповторов, переиспользованием движка и двумя измерениями времени. Генерация Android прошла, в CI проверяются 100 комбинаций и остальная регрессия.

1069: добавлено расширение сцены на экране разговора, три вида и направления звёзд. Изолированные короткие вопросы о времени и проверка связи дополнены быстрым маршрутом. Локальная генерация и проверка diff прошли; Android CI проверяет геометрию и регрессию.

Свободный разговор: история формируется как последние две валидные пары сообщений, текущий вопрос передаётся отдельно. Подготовлены сто прогонов ограничений истории и инструментальный тест на настоящем Qwen на Android-эмуляторе.

Первый цикл: просмотр 1071 → обработка результата Qwen → защита границ строк → тестовые сценарии. Реальная проверка использует ту же модель, что и 1071. Выпуск не запускается этой веткой.
