package ai.dreibus.lanalive

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses only fixed synthetic prompts. User turns and history are never loaded. */
class LanaModelDialogueInstrumentation : Instrumentation() {
    private data class Scenario(
        val name: String,
        val text: String,
        val expected: Regex,
        val previous: List<LanaLegacyTurn> = emptyList()
    )
    private data class Observation(val outcome: String, val snippet: String, val elapsedMs: Long = 0, val pssKiB: Long = 0)

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        var model: LanaLocalInference? = null
        val report = StringBuilder("\n")
        try {
            runOnMainSync { model = LanaLocalInference(targetContext) }
            val inference = checkNotNull(model)
            val scenarios = listOf(
                Scenario("arithmetic", "Сколько будет 12 плюс 5? Ответь одним числом.", Regex("\\b17\\b")),
                Scenario("context", "Как называется город из предыдущего сообщения? Ответь одним словом.",
                    Regex("риг[ауе]", RegexOption.IGNORE_CASE),
                    listOf(LanaLegacyTurn("user", "Мой город — Рига."), LanaLegacyTurn("assistant", "Поняла."))),
                Scenario("knowledge", "Назови столицу Франции одним словом.", Regex("париж", RegexOption.IGNORE_CASE)),
                Scenario("german_capital", "Как называется столица Германии? Напиши название по-русски.", Regex("берлин", RegexOption.IGNORE_CASE)),
                Scenario("definition", "Что такое компас? Ответь одним коротким предложением.",
                    Regex("направлен|ориентир|сторон[уы] света", RegexOption.IGNORE_CASE))
            )
            var matched = 0
            for (scenario in scenarios) {
                val result = observe(inference, scenario)
                if (result.outcome == "matched") matched++
                report.appendLine("${scenario.name}: ${result.outcome}; elapsed_ms=${result.elapsedMs}; callback_pss_kib=${result.pssKiB}; synthetic answer=${result.snippet}")
            }
            report.appendLine("LANA_MODEL_SIZE_BENCHMARK_COMPLETE matched=$matched/${scenarios.size}")
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (error: Exception) {
            report.appendLine("LANA_MODEL_EVALUATION_FAILED ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
        } finally {
            runOnMainSync { model?.close() }
        }
    }

    private fun observe(inference: LanaLocalInference, scenario: Scenario): Observation {
        val done = CountDownLatch(1)
        var result = Observation("missing_callback", "")
        lateinit var operation: LanaConversationRuntime.Cancel
        val startedAt = android.os.SystemClock.elapsedRealtime()
        runOnMainSync {
            val complete: (String?, String?) -> Unit = { answer, error ->
                val outcome = when {
                    error != null -> "runtime_error"
                    answer.isNullOrBlank() -> "empty"
                    LanaOfflineCore.checkedModelReply(scenario.text, answer) == LanaOfflineCore.REPHRASE -> "echo"
                    scenario.expected.containsMatchIn(answer) -> "matched"
                    else -> "unexpected_answer"
                }
                result = Observation(outcome, answer.orEmpty().replace(Regex("\\s+"), " ").take(160),
                    android.os.SystemClock.elapsedRealtime() - startedAt, android.os.Debug.getPss())
                done.countDown()
            }
            operation = inference.ask(scenario.text, scenario.previous, complete = complete)
        }
        if (!done.await(120, TimeUnit.SECONDS)) {
            runOnMainSync { operation.cancel() }
            error("timeout at ${scenario.name}")
        }
        return result
    }
}
