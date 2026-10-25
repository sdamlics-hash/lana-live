from pathlib import Path

def install(out: Path, root: Path):
    src = out/'app/src/main/java/ai/dreibus/lanalive'
    tests = out/'app/src/test/java/ai/dreibus/lanalive'
    for name in ('LanaDialogueStorage.kt', 'LanaCoordinator.kt', 'LanaProjectMemory.kt', 'LanaProjectMemoryStore.kt', 'LanaProjectMemoryBackup.kt'):
        (src/name).write_text((root/'android_team/src'/name).read_text())
    for name in ('LanaDurableHistoryTest.kt', 'LanaCoordinatorTest.kt', 'LanaProjectMemoryTest.kt', 'LanaProjectMemoryBackupTest.kt'):
        (tests/name).write_text((root/'android_team/tests'/name).read_text())
    p = src/'MainActivity.kt'
    s = p.read_text()
    def change(old, new):
        nonlocal s
        if s.count(old) != 1: raise ValueError('Continuity anchor not unique: '+old[:80])
        s = s.replace(old,new)
    change('    private val legacyDialogHistory = LanaLegacyHistory()', '''    private val dialogueStorage by lazy { LanaDialogueStorage(this) }
    private val legacyDialogHistory by lazy { LanaLegacyHistory(
        { scope -> dialogueStorage.read(scope) },
        { scope, raw -> dialogueStorage.write(scope, raw) }
    ) }''')
    change('        val priorTurns = legacyDialogHistory.snapshot(historyScope)', '''        val priorTurns = if (legacyMode) legacyDialogHistory.snapshot(historyScope) else emptyList()
        legacyDialogHistory.storageWarning?.let { voiceDiagnostic(it) }''')
    change('                if (legacyMode && serverSucceeded) legacyDialogHistory.commit(historyScope, question, reply)', '''                if (legacyMode && serverSucceeded && !legacyDialogHistory.commit(historyScope, question, reply)) {
                    legacyDialogHistory.storageWarning?.let { voiceDiagnostic(it) }
                }''')
    change('    private fun askFromLana(question: String) {', '''    private fun askFromLana(question: String) {
        if (LanaProjectMemory.matches(question)) {
            val reply = LanaProjectMemoryGateway.handle(this,question)
                ?: "Команда памяти проектов не распознана. Ничего не изменено."
            speakFromLana(reply)
            return
        }
        if (LanaCoordinator.handles(question)) {
            chatRequestStartedAt = 0L
            try {
                val rows = teamJournal().records()
                val followUp = LanaCoordinator.followUp(question, rows)
                if (followUp != null) { dispatchVoiceTeamTask(followUp); return }
                val local = LanaCoordinator.reply(question, rows)
                if (local != null) { speakFromLana(local); return }
                val command = LanaCoordinator.memoryCommand(question, rows)
                if (command != null) {
                    val prefs = getSharedPreferences("lana_workspace_v1", MODE_PRIVATE)
                    val reply = LanaLocalWorkspace(prefs.getString("records", org.json.JSONObject().put("tasks", org.json.JSONArray()).put("notes", org.json.JSONArray()).toString())!!) {
                        prefs.edit().putString("records", it).commit()
                    }.handle(command)
                    speakFromLana(reply ?: "Запись не создана; проверь текст команды.")
                    return
                }
                speakFromLana("Назови текст уточнения после команды. Ничего не отправлено.")
            } catch (error: IllegalArgumentException) {
                speakFromLana(error.message ?: "Команда неполная. Ничего не отправлено.")
            } catch (_: Exception) { speakFromLana("Журнал команды не прочитан. Записи не изменены; ничего не отправлено.") }
            return
        }''')
    change('    internal fun voiceSessionReport(): String {', '    internal fun clearSavedDialogueContext(): Boolean = dialogueStorage.clear()\n    internal fun voiceSessionReport(): String {')
    p.write_text(s)
    controls = src/'LanaConversationControls.kt'
    controls.write_text(controls.read_text()
        .replace('Выбор заметок для передачи будет снят.', 'Выбор заметок для передачи будет снят; прежний локальный контекст будет удалён.')
        .replace('                                activity.recreate()', '''                                if (!activity.clearSavedDialogueContext()) Toast.makeText(activity, "Новый разговор создан, но прежний локальный контекст не удалось очистить", Toast.LENGTH_LONG).show()
                                activity.recreate()'''))
    panel = src/'LanaTeamPanel.kt'
    text = panel.read_text().replace('            panel.addView(label("Лана — координатор команды"))', '            panel.addView(label("Лана — координатор команды"))\n            panel.addView(label(LanaCoordinator.summary(journal.records())))')
    panel.write_text(text)
    # Final UI changes follow the generator so the same navigation is used by all APK builds.
    workspace = src/'LanaWorkspace.kt'
    ws = workspace.read_text().replace('        addPanel(settings)', '''        settings.addView(text("Контекст беседы хранится зашифрованным только на этом устройстве: последние 8 пар реплик, до 16000 символов. Он привязан к серверу, аккаунту и разговору. Новый разговор начинает контекст заново. Это не вся история ChatGPT."))
        addPanel(settings)''')
    workspace.write_text(ws)
    typed = src/'LanaTypedDialogue.kt'
    typed.write_text(typed.read_text().replace('"Что ты умеешь", "Передай команде задание: "', '"Что ты умеешь", "Статус команды", "Что ждёт проверки", "Сохрани последний ответ команды в память", "Уточни у команды: ", "Передай команде задание: "'))
