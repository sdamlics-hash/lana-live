package ai.dreibus.lanalive

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Downloads are explicit user actions in Android or browser; LANA retains no INTERNET permission. */
internal class LanaOfflineSpeechSetup(private val activity:MainActivity) {
    private var recognizer:SpeechRecognizer?=null
    private var status:TextView?=null
    private var generation=0
    private fun report(text:String){if(!activity.isDestroyed)status?.text=text}
    private fun intent()=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
    }
    private fun start(download:Boolean){
        if(!activity.memoryTransferAllowed()){report("Останови разговор перед настройкой.");return}
        if(Build.VERSION.SDK_INT<33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)){
            report("Системный офлайн-распознаватель недоступен. Пока доступен текст.");return
        }
        close()
        val id=generation
        try{
            val r=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);recognizer=r
            report("Проверяю русский языковой пакет…")
            r.checkRecognitionSupport(intent(),activity.mainExecutor,object:RecognitionSupportCallback{
                override fun onSupportResult(support:RecognitionSupport){
                    if(id!=generation)return
                    val state=LanaOfflineSetupPolicy.speech(support.installedOnDeviceLanguages,support.pendingOnDeviceLanguages,support.supportedOnDeviceLanguages)
                    when(state){
                        LanaOfflineSetupPolicy.Speech.INSTALLED->report("Русское распознавание установлено. Проверь разговор в авиарежиме.")
                        LanaOfflineSetupPolicy.Speech.PENDING->report("Android ожидает загрузку русского пакета. Проверь позже.")
                        LanaOfflineSetupPolicy.Speech.UNSUPPORTED->report("Этот системный распознаватель не поддерживает русский офлайн. Нужен другой локальный движок.")
                        LanaOfflineSetupPolicy.Speech.DOWNLOAD->{
                            if(!download){report("Русский пакет доступен, но не установлен. Нажми «Загрузить русский пакет».");return}
                            try{
                                if(Build.VERSION.SDK_INT>=34)r.triggerModelDownload(intent(),activity.mainExecutor,object:ModelDownloadListener{
                                    override fun onProgress(completedPercent:Int){if(id==generation)report("Загрузка Android: $completedPercent%")}
                                    override fun onSuccess(){if(id==generation)report("Android сообщил об успешной загрузке. Нажми проверку пакета.")}
                                    override fun onScheduled(){if(id==generation)report("Android запланировал загрузку. Нажми проверку позднее.")}
                                    override fun onError(error:Int){if(id==generation)report("Android не загрузил пакет, код $error.")}
                                })else{r.triggerModelDownload(intent());report("Запрос загрузки передан Android. Проверь пакет позже.")}
                            }catch(_:Exception){report("Android не запустил загрузку.")}
                        }
                    }
                }
                override fun onError(error:Int){if(id==generation)report("Android не подтвердил наличие русского пакета, код $error.")}
            })
        }catch(_:Exception){report("Не удалось проверить локальное распознавание.")}
    }
    fun mount(panel:LinearLayout){
        status=TextView(activity).apply{text="Русский микрофон: пакет ещё не проверен";setTextColor(0xff342c2a.toInt())}
        panel.addView(status)
        fun button(label:String,action:()->Unit){panel.addView(Button(activity).apply{text=label;setOnClickListener{action()}})}
        button("Проверить русский офлайн-микрофон"){start(false)}
        button("Загрузить русский пакет Android"){start(true)}
        button("Открыть настройки системного голоса"){
            runCatching{activity.startActivity(Intent("com.android.settings.TTS_SETTINGS"))}.onFailure{
                runCatching{activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))}
            }
        }
        button("Скачать локальную модель Qwen3 (один раз)"){
            runCatching{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(LanaModelImport.SOURCE)))}.onFailure{report("Не удалось открыть браузер.")}
        }
        val model=java.io.File(java.io.File(activity.filesDir,"lana-local-model"),LanaModelImport.NAME)
        panel.addView(TextView(activity).apply{
            text=if(model.isFile)"Файл локальной модели найден; запуск ещё проверяется в разговоре." else "Локальная модель не импортирована. Сначала скачай файл, затем нажми «Импорт локальной модели Qwen3»."
            setTextColor(0xff342c2a.toInt())
        })
    }
    fun close(){generation++;runCatching{recognizer?.destroy()};recognizer=null}
}
