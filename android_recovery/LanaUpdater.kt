package ai.dreibus.lanalive

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** User-initiated update and a manual recovery path share package verification. */
internal class LanaUpdater(private val activity:MainActivity){
    private val dir=File(activity.cacheDir,"lana-updates")
    private val apk=File(dir,"offline-update.apk")
    private lateinit var label:TextView
    private lateinit var button:Button
    private var busy=false
    private var pending=false
    private fun version()=activity.packageManager.getPackageInfo(activity.packageName,0).let{if(Build.VERSION.SDK_INT>=28)it.longVersionCode else it.versionCode.toLong()}
    private fun ui(s:String,working:Boolean=false){if(activity.isDestroyed||activity.isFinishing||!::label.isInitialized)return;busy=working;label.text=s;button.isEnabled=!working}
    private val picker=activity.registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)stage(uri)
    }
    fun install(anchor:TextView){
        val parent=anchor.parent as? LinearLayout?:return
        label=TextView(activity).apply{textSize=15f;setTextColor(0xff237330.toInt())}
        button=Button(activity).apply{
            text="↻ ОБНОВИТЬ LANA · "+version();contentDescription="lana_update"
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList=android.content.res.ColorStateList.valueOf(0xff237d36.toInt())
            setOnClickListener{
                if (canUpdate()) checkChannel()
            }
            setOnLongClickListener {
                if (canUpdate()) picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
                true
            }
        }
        val i=parent.indexOfChild(anchor);parent.addView(button,i);parent.addView(label,i+1)
        ui("Сборка "+version()+" · удержи кнопку для выбора APK")
    }
    @Suppress("DEPRECATION") private fun verify(file:File, expected: Long? = null){
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm=activity.packageManager
        val current=pm.getPackageInfo(activity.packageName,flags)
        val next=pm.getPackageArchiveInfo(file.absolutePath,flags)?:error("Invalid APK")
        fun signers(info:android.content.pm.PackageInfo):Set<String>{
            val signatures=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map{it.toCharsString()}.toSet()
        }
        val v=if(Build.VERSION.SDK_INT>=28)next.longVersionCode else next.versionCode.toLong()
        require(expected == null || v == expected)
        require(LanaOfflineSetupPolicy.updateAllowed(current.packageName,next.packageName,version(),v,signers(current),signers(next)))
    }
    private fun canUpdate(): Boolean {
        if (busy) return false
        if (activity.memoryTransferAllowed()) return true
        ui("Сначала останови разговор.")
        return false
    }

    private fun checkChannel() {
        transfer("Проверяю обновление…", "Не удалось скачать или проверить обновление. Проверь интернет и повтори.") { part ->
            val manifest = open(CHANNEL) { input ->
                val bytes = java.io.ByteArrayOutputStream().also { output ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 16000)
                        output.write(buffer, 0, count)
                    }
                }.toByteArray()
                LanaUpdateManifest.parse(bytes.toString(Charsets.UTF_8))
            }
            if (manifest.version <= version()) return@transfer false
            open(manifest.url) { input -> copy(input, part, manifest.size) }
            require(part.length() == manifest.size)
            val digest = MessageDigest.getInstance("SHA-256")
            part.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == manifest.sha256)
            verify(part, manifest.version)
            true
        }
    }

    private fun stage(uri: Uri) {
        if (!canUpdate()) return
        transfer("Проверяю APK…", "APK не прошёл проверку: нужна более новая LANA с прежней подписью.") { part ->
            requireNotNull(activity.contentResolver.openInputStream(uri)).use { input ->
                copy(input, part, 250L * 1024 * 1024)
            }
            verify(part)
            true
        }
    }

    private fun copy(input: InputStream, file: File, limit: Long) {
        file.outputStream().use { output ->
            val buffer = ByteArray(65536)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit)
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        }
    }

    private fun transfer(progress: String, error: String, stage: (File) -> Boolean) {
        ui(progress, true)
        Thread {
            val part = File(dir, "offline-update.part.apk")
            val result = runCatching {
                check(dir.isDirectory || dir.mkdirs())
                if (!stage(part)) return@runCatching false
                android.system.Os.rename(part.absolutePath, apk.absolutePath)
                true
            }
            part.delete()
            activity.runOnUiThread {
                ui(when (result.getOrNull()) {
                    true -> "Подпись и версия проверены."
                    false -> "Установлена актуальная версия."
                    null -> error
                })
                if (result.getOrNull() == true && canUpdate()) openInstaller()
            }
        }.start()
    }

    private fun <T> open(address: String, read: (InputStream) -> T): T {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https" && url.userInfo == null && url.port in setOf(-1, 443))
            require(url.host in HOSTS)
            val connection = url.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "LANA-Updater")
            try {
                val code = connection.responseCode
                if (code == 200) return connection.inputStream.use(read)
                require(code in setOf(301, 302, 303, 307, 308))
                url = URL(url, requireNotNull(connection.getHeaderField("Location")))
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects")
    }

    private companion object {
        const val CHANNEL = "https://raw.githubusercontent.com/sdamlics-hash/lana-live/lana-offline-channel/lana-update.json"
        val HOSTS = setOf("raw.githubusercontent.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }

    private fun openInstaller(){
        if(activity.isDestroyed||activity.isFinishing)return
        try{
            if(Build.VERSION.SDK_INT>=26&&!activity.packageManager.canRequestPackageInstalls()){
                pending=true;ui("Разреши установку именно из LANA и вернись.")
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.packageName)));return
            }
            pending=false
            val uri=FileProvider.getUriForFile(activity,activity.packageName+".updates",apk)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            ui("Подтверди обновление в Android.")
        }catch(_:Exception){pending=false;ui("Не удалось открыть установщик. Выбери APK повторно.")}
    }
    fun resume(){
        if(pending && !busy && activity.packageManager.canRequestPackageInstalls()){
            pending=false;ui("Повторно проверяю APK…",true)
            Thread{
                val ok=runCatching{verify(apk)}.isSuccess
                activity.runOnUiThread{if(ok){ui("APK проверен.");openInstaller()}else ui("APK не прошёл повторную проверку.")}
            }.start()
        }
    }
}
