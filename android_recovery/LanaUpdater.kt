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

/** Local file update, with explicit browser access for obtaining files; never contacts a server itself. */
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
                if(!activity.memoryTransferAllowed()){ui("Сначала останови разговор.");return@setOnClickListener}
                android.app.AlertDialog.Builder(activity).setTitle("Обновить LANA")
                    .setItems(arrayOf("Выбрать скачанный APK","Открыть страницу обновлений в браузере")){_,which->
                        if(which==0)picker.launch(arrayOf("application/vnd.android.package-archive","application/octet-stream","*/*"))
                        else runCatching{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/sdamlics-hash/lana-live/releases")))}.onFailure{ui("Не удалось открыть браузер.")}
                    }.show()
            }
        }
        val i=parent.indexOfChild(anchor);parent.addView(button,i);parent.addView(label,i+1)
        ui("Сборка "+version()+" · локальное обновление с проверкой подписи")
    }
    @Suppress("DEPRECATION") private fun verify(file:File){
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm=activity.packageManager
        val current=pm.getPackageInfo(activity.packageName,flags)
        val next=pm.getPackageArchiveInfo(file.absolutePath,flags)?:error("Invalid APK")
        fun signers(info:android.content.pm.PackageInfo):Set<String>{
            val signatures=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map{it.toCharsString()}.toSet()
        }
        val v=if(Build.VERSION.SDK_INT>=28)next.longVersionCode else next.versionCode.toLong()
        require(LanaOfflineSetupPolicy.updateAllowed(current.packageName,next.packageName,version(),v,signers(current),signers(next)))
    }
    private fun stage(uri:Uri){
        if(busy)return
        ui("Проверяю APK…",true)
        Thread{
            val part=File(dir,"offline-update.part.apk")
            val ok=runCatching{
                check(dir.isDirectory||dir.mkdirs())
                requireNotNull(activity.contentResolver.openInputStream(uri)).use{input->
                    part.outputStream().use{output->
                        val buffer=ByteArray(65536);var total=0L
                        while(true){val n=input.read(buffer);if(n<0)break;require(n>0);total+=n;require(total<=250L*1024*1024);output.write(buffer,0,n)}
                        output.fd.sync()
                    }
                }
                verify(part)
                check(!apk.exists()||apk.delete());check(part.renameTo(apk))
            }.isSuccess
            part.delete()
            activity.runOnUiThread{
                if(!ok)ui("APK не прошёл проверку: нужна более новая LANA с прежней подписью.")
                else{ui("Подпись и версия проверены.");openInstaller()}
            }
        }.start()
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
