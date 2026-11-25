package ai.dreibus.lanalive

import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

/** Compile/API compatibility probe only. Must not be called from UI.
 * Production worker, session cancellation and real-model test are separate gates.
 */
internal object LanaLocalModelProbe {
    fun answer(model: File, cache: File, question: String): String {
        check(Looper.myLooper() != Looper.getMainLooper())
        require(model.isFile && model.name == LanaModelImport.NAME)
        require(question.isNotBlank() && question.length <= 4000)
        val engine = Engine(EngineConfig(modelPath=model.absolutePath, backend=Backend.CPU(), cacheDir=cache.absolutePath))
        engine.initialize()
        try {
            val conversation = engine.createConversation(ConversationConfig(
                systemInstruction=Contents.of("Ты LANA. Отвечай кратко на русском. Ты локальная модель без интернета. Не выдумывай выполненные действия или доступ к внешним данным.")
            ))
            try {
                val reply = conversation.sendMessage(question + "\n/no_think").toString().trim()
                require(reply.isNotBlank()) { "Локальная модель вернула пустой ответ" }
                return reply
            } finally { conversation.close() }
        } finally { engine.close() }
    }
}
