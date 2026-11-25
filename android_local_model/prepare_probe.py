from pathlib import Path
import shutil, subprocess, sys, re
ROOT=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve()
phase=sys.argv[2]
src=out/'app/src/main/java/ai/dreibus/lanalive'
tests=out/'app/src/test/java/ai/dreibus/lanalive'
if phase=='dependency':
    subprocess.run([sys.executable,str(ROOT/'android_offline/patch_offline.py'),str(out)],check=True)
    root_build=out/'build.gradle.kts'
    root_text=root_build.read_text()
    old='id("org.jetbrains.kotlin.android") version "2.1.0"'
    assert root_text.count(old)==1, "Unexpected Kotlin plugin version"
    root_build.write_text(root_text.replace(old,'id("org.jetbrains.kotlin.android") version "2.4.0"'))
    p=out/'app/build.gradle.kts'
    s=p.read_text()
    # Gradle accepts multiple dependencies blocks; do not assume the original whitespace.
    dependency = 'implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")'
    assert dependency not in s
    s += '\n\ndependencies {\n    ' + dependency + '\n}\n'
    old = 'kotlinOptions { jvmTarget = "1.8" }'
    assert s.count(old) == 1, "Unexpected Kotlin JVM options"
    s = s.replace(old, '')
    s += '\n\nkotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }\n'
    p.write_text(s)
    for f in (out/'build.gradle.kts',out/'settings.gradle.kts',out/'gradle/libs.versions.toml'):
        if f.exists():
            for line in f.read_text().splitlines():
                if 'kotlin' in line.lower() or 'com.android' in line.lower(): print(f.name+': '+line.strip())
    shutil.copyfile(ROOT/'android_local_model/LanaModelImport.kt',src/'LanaModelImport.kt')
    shutil.copyfile(ROOT/'android_local_model/ImportChecks.kt',tests/'LanaModelImportChecks.kt')
    (tests/'LanaModelImportTest.kt').write_text('package ai.dreibus.lanalive\nimport org.junit.Test\nclass LanaModelImportTest { @Test fun tenImportScenarios() { main() } }\n')
elif phase=='api':
    shutil.copyfile(ROOT/'android_local_model/LanaLocalModelProbe.kt',src/'LanaLocalModelProbe.kt')
else: raise ValueError("unknown phase")
