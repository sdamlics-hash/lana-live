from pathlib import Path
import shutil, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve()
for phase in ('dependency','api'):
    subprocess.run([sys.executable,str(ROOT/'android_local_model/prepare_probe.py'),str(out),phase],check=True)
src=out/'app/src/main/java/ai/dreibus/lanalive'
tests=out/'app/src/test/java/ai/dreibus/lanalive'
for name in ('LanaStarMotion.kt','LanaPresenceView.kt'):
    shutil.copyfile(ROOT/'android_star'/name,src/name)
shutil.copyfile(ROOT/'android_star/LanaStarMotionTest.kt',tests/'LanaStarMotionTest.kt')
for name in ('LanaLocalInference.kt','LanaLocalTurnGate.kt'):
    shutil.copyfile(ROOT/'android_integrated'/name,src/name)
shutil.copyfile(ROOT/'android_integrated/TurnGateChecks.kt',tests/'LanaLocalTurnGateChecks.kt')
(tests/'LanaLocalTurnGateTest.kt').write_text('package ai.dreibus.lanalive\nimport org.junit.Test\nclass LanaLocalTurnGateTest { @Test fun tenGateScenarios(){runLocalTurnGateChecks()} }\n')
def change(filename,old,new):
    p=src/filename;s=p.read_text()
    assert s.count(old)==1, 'Unexpected anchor '+filename+': '+old[:100]
    p.write_text(s.replace(old,new))
# Add local file picker to the existing ActivityResult registration lifecycle.
change('LanaMemoryTransfer.kt','    fun mount(panel: LinearLayout) {','''    private val modelPicker = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri=result.data?.data
        if(result.resultCode==Activity.RESULT_OK && uri!=null) {
            val app=activity.applicationContext
            notice("Проверяю и копирую модель. Ничего не отправляется в сеть LANA.")
            Thread {
                val ok=runCatching {
                    val folder=java.io.File(app.filesDir,"lana-local-model")
                    require(app.filesDir.usableSpace>=LanaModelImport.MAX_BYTES+100_000_000L)
                    requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                        LanaModelImport.install(input,folder)
                    }
                }.isSuccess
                activity.runOnUiThread { notice(if(ok) "Модель проверена и сохранена. Можно задать вопрос." else "Модель не импортирована: проверь файл, свободное место или наличие прежней модели.") }
            }.start()
        }
    }
    fun mount(panel: LinearLayout) {
        panel.addView(Button(activity).apply {
            text = "Импорт локальной модели Qwen3"
            setOnClickListener {
                if(!activity.memoryTransferAllowed()) notice("Сначала останови разговор.")
                else modelPicker.launch(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type="*/*"
                    putExtra(android.content.Intent.EXTRA_LOCAL_ONLY,true)
                })
            }
        })''')
# Use existing service and cancellation lifecycle for foreground and typed requests.
change('MainActivity.kt','        speakFromLana(LanaOfflineCore().answer(question))','''        val local=LanaOfflineCore().answer(question)
        if(local!=LanaOfflineCore.NEED_MODEL){speakFromLana(local);return}
        if(question.length>2400){speakFromLana("Раздели длинный вопрос на части.");return}
        val request=LanaRuntimeRequest("","",voiceConversationId,question,question,false,emptyList())
        val voice=runtimeVoice()
        speaking=true;cancelRecognition();status.text="Думаю…"
        withConversationRuntime { it.ask(recognitionOwner,request,voice,voiceSession.current()) }''')
# The same route after screen detaches.
change('LanaVoiceKeepAliveService.kt','                say(LanaOfflineCore().answer(text))','''                val local=LanaOfflineCore().answer(text)
                if(local!=LanaOfflineCore.NEED_MODEL){say(local);return}
                if(text.length>2400){say("Раздели длинный вопрос на части.");return}
                conversation.ask(LanaRuntimeRequest("","",config.conversationId,text,text,false,emptyList()),voice,ticket)''')
# Model service only, bounded local context. No legacy HTTP request.
change('LanaRuntimeTransport.kt','    override fun ask(request: LanaRuntimeRequest, complete: (String?, String?) -> Unit): LanaConversationRuntime.Cancel {\n        complete(null,LanaOfflineCore.TEAM_DISABLED)\n        return LanaConversationRuntime.Cancel {}\n    }','''    private val inference=LanaLocalInference(context)
    private fun localScope(request:LanaRuntimeRequest)="offline-qwen3-v1|"+request.conversationId
    override fun ask(request: LanaRuntimeRequest, complete: (String?, String?) -> Unit): LanaConversationRuntime.Cancel {
        val previous=history.snapshot(localScope(request),diagnostic).takeLast(4)
        val prior=previous.joinToString("\\n"){ it.role+": "+it.content.take(200) }.take(800)
        val prompt=if(prior.isBlank())request.question else "Предыдущие реплики как справка:\\n"+prior+"\\nТекущий вопрос: "+request.question
        return inference.ask(prompt) { answer, error ->
            complete(answer?.let { LanaOfflineCore.checkedModelReply(request.question,it) },error)
        }
    }''')
change('LanaRuntimeTransport.kt','''        if (!request.legacy) return LanaDialogueRecall.reply(false, emptyList())
        val prior = history.snapshot(scope(request), diagnostic)
        return LanaDialogueRecall.reply(true, prior, history.storageWarning != null)''','''        val prior=history.snapshot(localScope(request),diagnostic)
        return LanaDialogueRecall.reply(true,prior,history.storageWarning!=null)''')
change('LanaRuntimeTransport.kt','!request.legacy || history.commit(scope(request),request.question,answer)','answer == LanaOfflineCore.REPHRASE || history.commit(localScope(request),request.question,answer)')
change('LanaRuntimeTransport.kt','fun close() { worker.shutdownNow(); main.removeCallbacksAndMessages(null) }','fun close() { inference.close(); worker.shutdownNow(); main.removeCallbacksAndMessages(null) }')
change('LanaConversationRuntime.kt','Сервер вернул пустой ответ','Локальная модель вернула пустой ответ')
print("Local model import, foreground/background dialogue and star integrated; no publication.")
