package ai.dreibus.lanalive

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal class LanaUpdater(private val activity: Activity) {
    private val directory = File(activity.cacheDir, "lana-updates")
    private val apk = File(directory, "update.apk")
    private val metadata = File(directory, "update.json")
    private val prefs = activity.getSharedPreferences("lana_updater", Activity.MODE_PRIVATE)
    private lateinit var button: Button
    private lateinit var label: TextView
    private var busy = false
    private val manifestUrl = "https://github.com/sdamlics-hash/lana-live/releases/latest/download/lana-update.json"
    private fun installedVersion(): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    fun install(anchor: TextView) {
        val parent = anchor.parent as? LinearLayout ?: return
        label = TextView(activity).apply { text = "Версия Ланы: ${installedVersion()}"; textSize = 13f }
        button = Button(activity).apply {
            text = "Обновить Лану"; contentDescription = "lana_update"
            setOnClickListener { if (!busy) check() }
        }
        val index = parent.indexOfChild(anchor)
        parent.addView(button, index); parent.addView(label, index + 1)
    }
    private fun ui(message: String, active: Boolean = false) {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                label.text = message; busy = active; button.isEnabled = !active
            }
        }
    }
    private fun check() {
        busy = true; ui("Проверяю новую версию…", true)
        Thread {
            try {
                val raw = read(manifestUrl, 16000).toString(Charsets.UTF_8)
                val next = LanaUpdateManifest.parse(raw)
                if (next.version <= installedVersion()) { ui("Установлена актуальная версия ${installedVersion()}"); return@Thread }
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    busy = false; button.isEnabled = true
                    AlertDialog.Builder(activity).setTitle("Обновить Лану до ${next.version}?")
                        .setMessage("Скачаю ${(next.size / 1024 / 1024.0).let { String.format(java.util.Locale.ROOT, "%.1f", it) }} МБ и открою установку поверх текущей Ланы. Данные приложения сохраняются при обновлении.")
                        .setNegativeButton("Позже", null).setPositiveButton("Скачать и установить") { _, _ -> download(next, raw) }.show()
                }
            } catch (_: Exception) { ui("Не удалось проверить обновление. Проверь интернет и нажми ещё раз.") }
        }.start()
    }
    private fun download(next: LanaUpdateManifest, raw: String) {
        if (busy) return
        busy = true; ui("Скачиваю обновление…", true)
        Thread {
            val partial = File(directory, "update.part")
            try {
                check(directory.mkdirs() || directory.isDirectory)
                if (!apk.isFile || !metadata.isFile || metadata.readText() != raw) {
                    val connection = connection(next.url)
                    try {
                        connection.inputStream.use { input -> partial.outputStream().use { output ->
                            val buffer = ByteArray(32768); var total = 0L; var lastProgress = -1
                            while (true) {
                                val count = input.read(buffer); if (count < 0) break
                                total += count; require(total <= next.size)
                                output.write(buffer, 0, count)
                                val progress = (total * 100 / next.size).toInt()
                                if (progress / 10 != lastProgress / 10) { lastProgress = progress; ui("Скачиваю обновление: $progress%", true) }
                            }
                        } }
                    } finally { connection.disconnect() }
                    verify(partial, next)
                    check(!apk.exists() || apk.delete()); check(partial.renameTo(apk))
                    metadata.writeText(raw)
                }
                verify(apk, next)
                activity.runOnUiThread { busy = false; button.isEnabled = true; openInstaller() }
            } catch (_: Exception) { partial.delete(); apk.delete(); metadata.delete(); ui("Обновление не установлено: загрузка или проверка файла не прошла. Можно повторить.") }
        }.start()
    }
    @Suppress("DEPRECATION")
    private fun verify(file: File, next: LanaUpdateManifest) {
        require(file.length() == next.size)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(32768); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == next.sha256)
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = activity.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Invalid APK")
        val current = activity.packageManager.getPackageInfo(activity.packageName, flags)
        val version = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        require(candidate.packageName == activity.packageName && version == next.version && version > installedVersion())
        val a = if (Build.VERSION.SDK_INT >= 28) candidate.signingInfo!!.apkContentsSigners else candidate.signatures!!
        val b = if (Build.VERSION.SDK_INT >= 28) current.signingInfo!!.apkContentsSigners else current.signatures!!
        require(a.isNotEmpty() && a.toSet() == b.toSet())
    }
    private fun openInstaller() {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
                prefs.edit().putBoolean("pending", true).apply()
                label.text = "Разреши установку обновлений из Ланы и вернись сюда. Это разрешение можно затем отозвать в Android."
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
                return
            }
            prefs.edit().putBoolean("pending", false).apply()
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".updates", apk)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            label.text = "Подтверди обновление в системном окне Android."
        } catch (_: Exception) { ui("Не удалось открыть установку. Нажми «Обновить Лану» ещё раз.") }
    }
    fun resume() {
        if (busy || !prefs.getBoolean("pending", false) || !apk.isFile || !metadata.isFile) return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) return
        busy = true
        Thread {
            try { verify(apk, LanaUpdateManifest.parse(metadata.readText())); activity.runOnUiThread { busy = false; openInstaller() } }
            catch (_: Exception) { prefs.edit().putBoolean("pending", false).apply(); ui("Скачанное обновление устарело или повреждено. Проверь обновление снова.") }
        }.start()
    }
    private fun connection(address: String): HttpsURLConnection {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https" && url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com"))
            val c = url.openConnection() as HttpsURLConnection
            c.instanceFollowRedirects = false; c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "LANA-Updater")
            val code = c.responseCode
            if (code == 200) return c
            val location = c.getHeaderField("Location"); c.disconnect()
            require(code in setOf(301, 302, 303, 307, 308) && location != null)
            url = URL(url, location)
        }
        error("Too many redirects")
    }
    private fun read(url: String, limit: Int): ByteArray {
        val c = connection(url)
        try { return c.inputStream.use { val bytes = it.readBytesLimited(limit); bytes } }
        finally { c.disconnect() }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
        while (true) { val n = read(buffer); if (n < 0) break; require(output.size() + n <= limit); output.write(buffer, 0, n) }
        return output.toByteArray()
    }
}
