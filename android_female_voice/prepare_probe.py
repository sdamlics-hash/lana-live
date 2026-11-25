from pathlib import Path
import shutil, subprocess, sys
root=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve()
aar=Path(sys.argv[2]).resolve()
subprocess.run([sys.executable,str(root/'android_recovery/prepare_recovery.py'),str(out)],check=True)
libs=out/'app/libs';libs.mkdir(exist_ok=True)
shutil.copyfile(aar,libs/'sherpa-onnx-static-1.12.14.aar')
build=out/'app/build.gradle.kts'
build.write_text(build.read_text()+'\n dependencies { implementation(files("libs/sherpa-onnx-static-1.12.14.aar")) }\n')
src=out/'app/src/main/java/ai/dreibus/lanalive'
shutil.copyfile(root/'android_female_voice/LanaFemaleVoiceProbe.kt',src/'LanaFemaleVoiceProbe.kt')
