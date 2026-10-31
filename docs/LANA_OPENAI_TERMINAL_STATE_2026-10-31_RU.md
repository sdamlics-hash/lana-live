# LANA — проверка завершённости ответа OpenAI (31.10.2026)

## План и критерии
1. Прочитать согласованные требования `LANA_ACCEPTANCE_MATRIX_RU.md`, `LANA_REQUIREMENTS_RU.md` и действующий `openai_transport`.
2. Отдельно от main устранить ситуацию, когда сервер получает от Responses API неполный или неудачный ответ, но озвучивает и сохраняет его как окончательный.
3. Принимать только верхнеуровневый `status="completed"`; не принимать `incomplete`, `failed`, `cancelled`, `queued`, `in_progress` и отсутствующий статус.
4. Учитывать, что `output[].status` необязателен: отсутствие разрешено, но явный статус, отличный от `completed`, запрещён. Текст брать только из сообщений `type="message"`, а не из reasoning/tool items.
5. Не запускать повторный запрос автоматически, не сохранять незавершённый ответ и не возвращать его как успешный HTTP 200. Попытка должна остаться учтённой в UsageGate.
6. Независимые GitHub Actions: серверные тесты + существующие Android workflow. При ошибке — анализ лога и исправление.

## Причина
До исправления код проверял наличие `output[].content[].text`, но не `status` ответа. Responses API допускает `status="incomplete"` с `incomplete_details.reason="max_output_tokens"`. Это могло выглядеть как готовая фраза, хотя модель остановилась посреди ответа. Для голосового помощника такой ложный успех опасен; повторение автоматически может дополнительно расходовать лимиты.

Документация: https://developers.openai.com/api/reference/resources/responses/methods/retrieve

## Журнал
- 31.10: просмотрены требования, PR №2–8, PR №57, код `lana_core/chat.py`, `lana_core/usage.py`, Android-клиент и тесты.
- Создана изолированная ветка `fix/lana-responses-terminal-state-20261031` от PR №57, без изменений main.
- В `chat.py` добавлена проверка терминального статуса и фильтрация только message; старый тестовый ответ обновлён под официальную схему.
- Добавлены 5 тестов: completed, optional item status, 5 неполных состояний, отсутствующий статус, HTTP 503 + 1 попытка + 0 записей в историю.
- Первый CI прогон 31.10 выявил 4 регрессии старых фикстурных тестов: в тестовых JSON отсутствовали `status` и `type=message`. Причина в фикстурах, не в Android. Исправлены 4 образца в `tests/test_openai_transport_security.py`; лог ошибки [#41188399640](https://github.com/sdamlics-hash/lana-live/actions/runs/41188399640).
- Повторный независимый CI: [Core Tests #41188457465](https://github.com/sdamlics-hash/lana-live/actions/runs/41188457465) — **SUCCESS, 89/89 тестов** (включая 5 новых); лог job #124500089555 проверен.
- [Voice + OpenAI Preview #41188457426](https://github.com/sdamlics-hash/lana-live/actions/runs/41188457426) — **SUCCESS**.
- [Voice Repair #41188457471](https://github.com/sdamlics-hash/lana-live/actions/runs/41188457471) — **SUCCESS**.
- [V3.8 Memory Consent #41188457405](https://github.com/sdamlics-hash/lana-live/actions/runs/41188457405) — **SUCCESS**.
- [V3.9 Connection Evidence #41188457409](https://github.com/sdamlics-hash/lana-live/actions/runs/41188457409) — **SUCCESS**.
- [V4.0 Voice Turns #41188457463](https://github.com/sdamlics-hash/lana-live/actions/runs/41188457463) — **SUCCESS**.
- 6/6 workflow успешны после исправления. Эти проверки **не доказывают** реальный OpenAI, Samsung или 24/7.

## Ограничения
Тесты используют только имитированный ответ OpenAI. Не проверяют настоящую VM, платные вызовы, русский TTS, фоновый wake-word или круглосуточную работу.

## Откат и точка продолжения
Не объединять PR, пока нет успешных CI. Для отката достаточно закрыть draft PR: main, реальная VM и Samsung не изменялись. Далее — безопасный staging с HTTPS и отдельная проверка настоящего голосового диалога при явном разрешении пользователя.

## Готовность
Голосовая Лана ≈60%, автономная Лана 24/7 ≈30%. Исправление защитного контракта не повышает проценты без проверки на реальном устройстве.
