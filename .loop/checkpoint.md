# LANA checkpoint — Batch B1 / 2026-10-25
Stable release 1043 unchanged; branch work/lana-memory-v2-batch.
Added: LanaProjectMemory (bounded explicit facts per project, source, timestamp, fail-safe saves), AndroidKeystore encrypted AtomicFile, dual voice/typed routing, separate export/import UI with confirmation, settings and tests.
Next: open single draft PR; Kotlin Android CI + emulator persistence screenshot; fix failures up to 5 loops. Only after green checks and backup verification consider signed in-place candidate. User to perform actual Samsung tests. No VM/GOLD/Kraken/API keys altered.
Important: Claude, Manus and entire ChatGPT history NOT connected; 24/7 microphone stability not independently proven.


## Latest checkpoint — 2026-11-22 / B2 clear approved portrait
Current stable: signed LANA 1047 / release lana-stable-1047 / GitHub Actions run 44954656885 SUCCESS, but this only verifies build+unit checks, not Samsung speech.
Confirmed visual defect: android_team/assets/lana_approved_1047.jpg 144x144, scaled in LanaPresenceView. Exact higher-detail original found: personal Library "Futuristiska LANA AI portreta animācijā.gif", 506x508, 6 frames. First frame matches approved art. Exported locally, not yet present in GitHub.
Working branch: work/lana-1048-clear-portrait-20261122 from 745518f9. Signed release and installed app untouched.
Next: integrate recovered 506x508 still into this isolated branch replacing low-resolution drawable, test visual resource pipeline and Android CI. Only after build/cert/hash verification publish LANA 1048. Real Samsung 20-min conversation, STOP and note-consent persistence still need physical confirmation. Keep no new package ID, no uninstall/data reset, no credential changes.

## B2 continuation — 2026-11-22 / source integrated
Recovered full-size first GIF frame was encoded to 506x508 JPEG (SHA-256 d49b2e74abd37ca42869592910e0d9164d9ab14d0d8065b3976d39df3cac552e) and uploaded as Git blob 721f0942a52689353e9bbfd1bcda647d0b8b38a9; verified byte-for-byte by matching local Git blob SHA. Committed to isolated branch at 8668259d70a2a4b1155277777a14dc4fd57a71b1, replacing 144x144 resource. Draft PR #67 compares to existing signed 1047 build branch; stable and device unchanged. CI: image dimension/hash check and exact generated drawable copy passed in run 45003835541; full Gradle and other PR checks remain IN_PROGRESS as last checked. Safe backup ref backup/lana-stable-1047-20261122 preserves baseline. Next: review CI conclusions, fix failures if any, and ONLY THEN consider signed 1048 candidate; 20-minute real Samsung acceptance remains outstanding. Do not report 1048 APK ready before evidence.

## Offline voice follow-up — 1053 source
Baseline 82c7b96 (1051). Updated voice settings refresh and preview selection. Generated project and identity checks passed. Next: compile and sign with original key, check 25 audible turns and STOP on Samsung. APK and device acceptance pending.

## 2026-11-26 — кандидат 1055
На Samsung видна 1051 и один свободный текстовый ответ. Звук и работа в авиарежиме не подтверждены. Архив 1053/1054 восстановлен и проверен по контрольным суммам. Ветка work/lana-voice-followup готовит 1055/1056. Следующий шаг после CI — короткий тест на Samsung: проба женского русского голоса, ответ, STOP, затем серия реплик в авиарежиме.

## 1057 — кнопка обновления
Ветка work/lana-button-update, основа 804f132. Канал lana-offline-channel, релиз lana-offline-1057, подписанный откат 1058 на код 1055. После CI подтвердить открытие опубликованного манифеста и совпадение APK. На Samsung отдельно проверить сообщение об актуальной версии, пробу голоса и STOP. Следующим выпуском для проверки реального обновления через кнопку должна быть версия выше 1058.

Результат 1057: GitHub Actions 45792314752 завершился успешно, включая модульные тесты, проверку прежнего сертификата у обоих APK и публикацию. Манифест прочитан извне, опубликованный APK совпал с подписанным артефактом; SHA-256 936e8763c3176f3616e3b85a4364e6be337ee866eaa18a96d4a460ed0146be60. На Samsung ещё нужны проверка кнопки, голос и установка следующей версии с канала.

## 1059 — ответ голосом
1057 установлена на Samsung, пробный голос слышен, ответ «Я на связи» пришёл текстом; журнал пишет о неподтверждённом голосе. 1059 убирает этот барьер и сохраняет удачную пробу из настроек. По зелёной кнопке ожидается обновление 1057 → 1059. Откат 1060 к коду 1057; после обновления нужна одна голосовая реплика на Samsung.

Выпуск 1059: первый запуск остановлен ошибками Maven 429. Повтор через зеркало прошёл, CI 45796416559, оба APK подписаны прежним сертификатом. Канал 1059 прочитан извне, скачанный APK совпал по размеру и SHA-256. Следующий шаг — зелёная кнопка и одна устная реплика на Samsung. Реальная озвучка на планшете пока не подтверждена.

## 1061 — задержка
1059 установилась через канал и заговорила на Samsung по сообщению пользователя. 1061 сокращает программную задержку после финала Android, сохраняя окно для очевидно незаконченных фраз. После CI проверить канал и затем задержку на Samsung. Откат 1062 возвращает 1059.

Выпуск 1061: Q200 и сборка прошли, CI 45797623234, обе подписи проверены. Канал 1061 отдаёт APK с совпадающими размером и SHA-256. На Samsung ещё проверить короткий вопрос и фразу с паузой. Последний замер 1059 — конец речи → текст 1411 мс для одной реплики; скорость 1061 на планшете пока не измерялась.

## 1063 — портрет
Ветка work/lana-portrait-1063. Портрет и орбита на месте прежнего шара. Откат 1064 к 1061. После CI проверить канал, затем на Samsung открыть разговор и произнести короткую реплику. Командный пилот остаётся отдельно в work/lana-team-pilot.

Выпуск 1063: тесты движения и сборка прошли в CI 45800836646, подписи 1063 и 1064 проверены. Канал отдаёт 1063, фактический размер и SHA-256 совпали. На Samsung проверить портрет, сближение звёзд при ответе и отзывчивость разговора.


## Следующий этап
На скрине 1063 портрет отображается. Голосовый фрагмент появился в 11:30:51, следом сообщение о завершении сеанса. Подтверждён дефект в коде: скрытие открытого экрана помечается как окончательное завершение. Связь именно этого события со скриншотом остаётся гипотезой до прогона на Samsung. После голоса: расширяемая сцена со звёздами на разных траекториях, затем панель GOLD12 справа для просмотра статуса.

## 1065 — фоновый разговор
На 1063 пользователь подтвердил остановку при смене приложений. 1065 включает фоновый режим при явном запуске разговора и не закрывает активный экранный сеанс при отказе Android запустить фоновый сервис. Переключение в Google на Samsung остаётся физической проверкой.

Выпуск 1065: канал обновлений уже отдаёт 1065. Публикующий шаг проверяет сборку, тесты, прежнюю подпись и совпадение скачанного APK с подписанным файлом до смены манифеста. Следующий шаг на Samsung: «Слушать», перейти в Google, сказать «Лана, ты тут?», проверить STOP.

## 1067 — разговор
1065 слушает в фоне на Samsung. Пакет 1067 сокращает затраты повторного запуска модели, отвечает локально на частые реплики, отбрасывает служебные повторы и измеряет подготовку и запуск озвучки. Откат 1068 возвращает 1065. После CI проверить публикацию и затем повторить разговор на Samsung.

Выпуск 1067: CI 45805938411, 100 сценариев и общие тесты прошли. Подпись сверена, канал отдаёт 1067, размер и SHA-256 APK совпали. На Samsung проверяем несколько подряд реплик, задержку до старта голоса и работу после перехода в Google.
