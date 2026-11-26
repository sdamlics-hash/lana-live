package ai.dreibus.lanalive

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.*
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Owns native resources on one background thread. No server, no automatic retries. */
internal class LanaLocalInference(context: Context) {
    private val app=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val dispatcher=Executors.newSingleThreadExecutor { r -> Thread(r,"LanaLocalInference") }.asCoroutineDispatcher()
    private val scope=CoroutineScope(SupervisorJob()+dispatcher)
    private val gate=LanaLocalTurnGate()
    private var closed=false
    // Accessed only on dispatcher.
    private var activeConversation:Conversation?=null
    private var activeId:Long?=null
    private var warmEngine: Engine? = null
    private var idleClose: Job? = null

    private fun releaseEngine() {
        val opened = warmEngine
        warmEngine = null
        runCatching { opened?.close() }
    }
    private val model get()=File(File(app.filesDir,"lana-local-model"),LanaModelImport.NAME)
    fun ask(text:String, previous: List<LanaLegacyTurn>, complete:(String?,String?)->Unit):LanaConversationRuntime.Cancel {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(!model.isFile) {
            complete(null,"Сначала импортируй локальную модель в Настройках LANA.")
            return LanaConversationRuntime.Cancel {}
        }
        if(text.isBlank() || text.length>4000) {
            complete(null,"Вопрос слишком длинный или пустой.");return LanaConversationRuntime.Cancel {}
        }
        val id=gate.begin() ?: run {
            complete(null,"Предыдущий локальный запрос ещё завершается. Повтори позже.");return LanaConversationRuntime.Cancel {}
        }
        scope.launch {
            var result:String?=null
            var failure: String? = null
            idleClose?.cancel()
            idleClose = null
            var conversation:Conversation?=null
            try {
                if(gate.accepts(id)) {
                    val opened = warmEngine ?: run {
                        val cache = File(app.cacheDir,"lana-local-inference").apply { mkdirs() }
                        val fresh = Engine(EngineConfig(modelPath=model.absolutePath,backend=Backend.CPU(),cacheDir=cache.absolutePath))
                        try { fresh.initialize() }
                        catch (error: Throwable) { runCatching { fresh.close() }; throw error }
                        warmEngine = fresh
                        fresh
                    }
                    if(gate.accepts(id)) {
                        val history = LanaOfflineCore.boundedHistory(previous).map {
                            if (it.role == "user") Message.user(it.content) else Message.model(it.content)
                        }
                        val c=opened.createConversation(ConversationConfig(systemInstruction=Contents.of(
                            "Ты LANA, локальный помощник. Отвечай кратко на русском. Отвечай на последний вопрос по смыслу. Не повторяй вопрос, заголовки и предыдущие реплики. Не выдумывай доступ к интернету или выполненные действия."
                        ), initialMessages = history))
                        conversation=c;activeConversation=c;activeId=id
                        val reply=StringBuilder()
                        var overflow=false
                        c.sendMessageAsync(text+"\n/no_think").collect { chunk ->
                            if(gate.accepts(id) && !overflow) {
                                val next=chunk.toString()
                                if(reply.length+next.length>16000) {
                                    overflow=true
                                    runCatching { c.cancelProcess() }
                                } else reply.append(next)
                            }
                        }
                        if (!overflow) result = LanaOfflineCore.visibleModelReply(reply.toString())
                        if (result == null) failure = if (overflow)
                            "Модель превысила лимит ответа. Попроси ответить короче."
                        else
                            "Локальная модель не дала готового ответа. Попробуй уточнить вопрос."
                    }
                }
            } catch (_: Throwable) {
                failure = "Локальная модель не смогла завершить запрос. Проверь файл модели и свободную память."
            }
            finally {
                activeConversation=null;activeId=null
                runCatching { conversation?.close() }
                if (failure != null || !gate.accepts(id)) releaseEngine()
                else idleClose = scope.launch {
                    delay(60_000L)
                    releaseEngine()
                }
                main.post {
                    val valid=gate.finish(id)
                    if(closed) shutdown()
                    else if (valid) complete(result, failure)
                }
            }
        }
        return LanaConversationRuntime.Cancel { if(gate.cancel(id)) cancelNative(id) }
    }
    private fun cancelNative(id:Long) {
        scope.launch {
            while(activeId==id && activeConversation!=null) {
                runCatching { activeConversation?.cancelProcess() }
                delay(100)
            }
        }
    }
    private fun shutdown() {
        scope.launch {
            idleClose?.cancel()
            releaseEngine()
            scope.cancel()
            dispatcher.close()
        }
    }
    fun close(){
        if(closed)return
        closed=true
        val id=gate.close()
        if(id==null)shutdown() else cancelNative(id)
    }
}
