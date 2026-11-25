from pathlib import Path
import shutil, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(ROOT/'android_offline/patch_offline.py'),str(out)],check=True)
src=out/'app/src/main/java/ai/dreibus/lanalive'
tests=out/'app/src/test/java/ai/dreibus/lanalive'
for name in ('LanaStarMotion.kt','LanaPresenceView.kt'):
    shutil.copyfile(ROOT/'android_star'/name,src/name)
shutil.copyfile(ROOT/'android_star/LanaStarMotionTest.kt',tests/'LanaStarMotionTest.kt')
