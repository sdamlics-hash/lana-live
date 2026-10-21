"""Prove an unconfigured release refuses before producing an unsigned/debug-signed release."""
import os
from pathlib import Path
import subprocess
import sys
root=Path(sys.argv[1])
env=os.environ.copy()
for name in ('LANA_ANDROID_KEYSTORE','LANA_ANDROID_STORE_PASSWORD','LANA_ANDROID_KEY_ALIAS','LANA_ANDROID_KEY_PASSWORD'):
    env.pop(name,None)
result=subprocess.run(['gradle','--no-daemon','assembleRelease'],cwd=root,env=env,capture_output=True,text=True,timeout=180)
assert result.returncode!=0 and 'LANA_RELEASE_SIGNING_REQUIRED' in result.stdout+result.stderr,'Expected specific signing refusal, not unrelated Gradle failure'
assert not list((root/'app/build/outputs').glob('**/*release*.apk')),'Unconfigured release must not emit a release APK'
print('Unconfigured release explicitly refused; no release APK emitted; no keys generated')
