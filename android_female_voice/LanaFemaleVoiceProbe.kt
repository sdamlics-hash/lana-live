package ai.dreibus.lanalive

import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/** Compile/API experiment, not yet wired to production speaker.
 * Model assets must be verified and provisioned separately. No network fallback.
 */
internal object LanaFemaleVoiceProbe {
    fun synthesize(modelDir: File, text: String, output: File, cancelled: () -> Boolean): Boolean {
        check(Looper.myLooper()!=Looper.getMainLooper())
        require(text.isNotBlank() && text.length<=1200)
        require(!output.exists())
        val model=File(modelDir,"ru_RU-irina-medium.onnx")
        val tokens=File(modelDir,"tokens.txt")
        val data=File(modelDir,"espeak-ng-data")
        require(model.isFile && tokens.isFile && data.isDirectory)
        if(cancelled())return false
        val engine=OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(
            vits=OfflineTtsVitsModelConfig(model=model.absolutePath,tokens=tokens.absolutePath,dataDir=data.absolutePath),
            numThreads=2,provider="cpu"
        ),maxNumSentences=1))
        try {
            val audio=engine.generateWithCallback(text,0,1f){ if(cancelled())0 else 1 }
            if(cancelled())return false
            require(audio.sampleRate==22050 && audio.samples.size>5512 && audio.samples.size<22050*120)
            require(audio.samples.all{it.isFinite()})
            if(!audio.save(output.absolutePath)||cancelled()){output.delete();return false}
            return true
        }catch(e:Exception){output.delete();throw e}
        finally{engine.release()}
    }
}
