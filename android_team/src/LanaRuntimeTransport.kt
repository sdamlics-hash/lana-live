package ai.dreibus.lanalive

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded worker and durable context are Service-owned. Credentials never enter diagnostics. */
internal class LanaRuntimeTransport(private val context: Context, private val diagnostic: (String) -> Unit = {}) : LanaConversationRuntime.Transport {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue(2),{ task -> Thread(task,"LanaDialogue").apply { isDaemon = true } })
    private val storage = LanaDialogueStorage(context)
    private val history = LanaLegacyHistory({ storage.read(it) },{ scope, raw -> storage.write(scope,raw) })
    private fun scope(request: LanaRuntimeRequest): String {
        val fingerprint = java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(request.token.toByteArray(Charsets.UTF_8)))
        return request.address+"|"+request.conversationId+"|"+request.legacy+"|"+fingerprint
    }
    override fun ask(request: LanaRuntimeRequest, complete: (String?, String?) -> Unit): LanaConversationRuntime.Cancel {
        val prior = if (request.legacy) history.snapshot(scope(request),diagnostic) else emptyList()
        if (request.legacy) {
            val error = LanaLegacyQuota.claim(context)
            if (error != null) { complete(null,error); return LanaConversationRuntime.Cancel {} }
        }
        val queuedAt = android.os.SystemClock.elapsedRealtime()
        var cancelled = false
        val cancellation = LanaHttpCancellation()
        val future = try { worker.submit {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val answer = runCatching { LanaChatClient.ask(request.address,request.token,request.conversationId,request.prompt,request.notes,request.legacy,prior,cancellation=cancellation) }
            val finishedAt = android.os.SystemClock.elapsedRealtime()
            val timing = LanaDialogueTiming(queuedAt,startedAt,finishedAt,prior.size / 2,answer.isSuccess)
            main.post {
                if (!cancelled) {
                    runCatching { diagnostic(timing.report()) }
                    if (answer.isSuccess) complete(answer.getOrNull(),null)
                    else {
                        val failure = answer.exceptionOrNull()
                        if (failure is LanaChatTimeout) {
                            runCatching { diagnostic("HTTP timeout stage=" + failure.stage + "; repeat=none") }
                        }
                        val reason = when (failure) {
                            is LanaChatTimeout -> failure.message ?: "Время ожидания связи истекло."
                            is LanaChatFailure -> failure.message ?: "Сервер отклонил запрос."
                            is java.net.SocketTimeoutException -> "Сервер не ответил вовремя."
                            is java.net.UnknownHostException -> "Адрес сервера не найден. Временный адрес мог измениться."
                            is javax.net.ssl.SSLException -> "Не удалось подтвердить защищённое соединение с сервером."
                            is IllegalArgumentException, is org.json.JSONException -> "Настройки запроса или формат ответа сервера несовместимы."
                            else -> "Связь с сервером прервалась."
                        }
                        complete(null,reason + " Автоматического повтора нет.")
                    }
                }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            complete(null,"Предыдущий запрос ещё завершается. Дождись его остановки и повтори вопрос.")
            return LanaConversationRuntime.Cancel {}
        }
        return LanaConversationRuntime.Cancel {
            cancelled = true
            cancellation.cancel { connection ->
                // disconnect() may wait on platform networking; never block STOP on the UI thread.
                Thread({ runCatching { connection.disconnect() } },"LanaHttpCancel").apply { isDaemon = true }.start()
            }
            future.cancel(true)
        }
    }
    fun accepted(request: LanaRuntimeRequest, answer: String): Boolean = !request.legacy || history.commit(scope(request),request.question,answer)
    fun close() { worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
