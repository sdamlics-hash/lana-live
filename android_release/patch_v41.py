"""Prepare monotonic V4.1 build and require explicit release signing, no keys generated."""
import importlib.util
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def make_preview(archive,destination):
    spec=importlib.util.spec_from_file_location('v40',ROOT/'android_dialogue/patch_v40.py')
    base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
    out=base.make_preview(archive,Path(destination))
    build=out/'app/build.gradle.kts';s=build.read_text()
    if s.count('versionCode = 5')!=1 or s.count('0.6.0-lana-voice-turns')!=1:raise ValueError('Unexpected V4.0 version; refusing an accidental downgrade')
    s=s.replace('versionCode = 5','versionCode = 6').replace('0.6.0-lana-voice-turns','0.6.1-lana-update-prep')
    config='''
// Signing inputs come only from a private build environment, never source control.
val lanaStore = System.getenv("LANA_ANDROID_KEYSTORE")
val lanaStorePassword = System.getenv("LANA_ANDROID_STORE_PASSWORD")
val lanaKeyAlias = System.getenv("LANA_ANDROID_KEY_ALIAS")
val lanaKeyPassword = System.getenv("LANA_ANDROID_KEY_PASSWORD")
val lanaReleaseConfigured = listOf(lanaStore, lanaStorePassword, lanaKeyAlias, lanaKeyPassword).all { !it.isNullOrBlank() }
if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } && !lanaReleaseConfigured) {
    throw GradleException("LANA_RELEASE_SIGNING_REQUIRED: configure the existing approved signing identity before building a release")
}
'''
    s=s.replace('\nandroid {',config+'\nandroid {',1)
    s=s.replace('    namespace =', '''    if (lanaReleaseConfigured) {
        signingConfigs {
            create("lanaRelease") {
                storeFile = file(lanaStore!!)
                storePassword = lanaStorePassword
                keyAlias = lanaKeyAlias
                keyPassword = lanaKeyPassword
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("lanaRelease")
    }
    namespace =''',1)
    s+='''
// Also protect implicit aggregate tasks such as `assemble`.
tasks.configureEach {
    if (name.contains("Release", ignoreCase = true) &&
        listOf("package", "bundle", "assemble", "sign").any { name.contains(it, ignoreCase = true) }) {
        doFirst {
            check(lanaReleaseConfigured) { "LANA_RELEASE_SIGNING_REQUIRED" }
        }
    }
}
'''
    build.write_text(s)
    layout=out/'app/src/main/res/layout/activity_main.xml';layout.write_text(layout.read_text().replace('LANA Live V4.0','LANA Live V4.1'))
    return out
if __name__=='__main__':make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
