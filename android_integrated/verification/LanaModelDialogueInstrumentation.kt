package ai.dreibus.lanalive

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs the actual production inference code with a pinned model in an isolated test app. */
class LanaModelDialogueInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        var model: LanaLocalInference? = null
        try {
            runOnMainSync { model = LanaLocalInference(targetContext) }
            val inference = checkNotNull(model)
            fun verify(question: String, previous: List<LanaLegacyTurn>, expected: Regex) {
                val done = CountDownLatch(1)
                var matched = false
                runOnMainSync {
                    inference.ask(question, previous) { answer, error ->
                        matched = error == null && answer != null &&
                            LanaOfflineCore.checkedModelReply(question, answer) != LanaOfflineCore.REPHRASE &&
                            expected.containsMatchIn(answer)
                        done.countDown()
                    }
                }
                check(done.await(120, TimeUnit.SECONDS) && matched) { "Expected model answer was not verified." }
            }
            verify("Сколько будет 12 плюс 5? Ответь одним числом.", emptyList(), Regex("\\b17\\b"))
            verify("Как называется город из предыдущего сообщения? Ответь одним словом.",
                listOf(LanaLegacyTurn("user", "Мой город — Рига."), LanaLegacyTurn("assistant", "Поняла.")),
                Regex("риг[ауе]", RegexOption.IGNORE_CASE))
            verify("Назови столицу Франции одним словом.", emptyList(),
                Regex("париж", RegexOption.IGNORE_CASE))
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "LANA_REAL_MODEL_OK\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "LANA_REAL_MODEL_FAILED: ${error.javaClass.simpleName}\n") })
        } finally {
            runOnMainSync { model?.close() }
        }
    }
}
