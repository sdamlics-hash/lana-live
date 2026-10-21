"""Inspect two APKs using Android SDK verifiers. No signing keys or device writes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

EXPECTED_PACKAGE='ai.dreibus.lanalive.voicepreview'
def sdk_tool(name):
    found=shutil.which(name)
    if found:return found
    root=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/nonexistent')/'build-tools'
    choices=sorted(root.glob('*/'+name),reverse=True)
    if not choices:raise RuntimeError('Android SDK tool unavailable: '+name)
    return str(choices[0])
def parse_verified_signers(output):
    values=re.findall(r'^(?:Signer #\d+|V[1-4](?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$',output,re.M)
    if not values:raise ValueError('Verified signer certificate fingerprint not found')
    return sorted(set(value.lower() for value in values))
def parse_package(output):
    match=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'",output,re.M)
    if not match:raise ValueError('APK package/version metadata unavailable')
    return {'package':match[1],'version_code':int(match[2]),'version_name':match[3]}
def inspect_apk(path):
    path=Path(path)
    verify=subprocess.run([sdk_tool('apksigner'),'verify','--verbose','--print-certs',str(path)],capture_output=True,text=True,timeout=40)
    if verify.returncode:raise ValueError('APK signature verification failed: '+path.name)
    metadata=subprocess.run([sdk_tool('aapt'),'dump','badging',str(path)],capture_output=True,text=True,timeout=30)
    if metadata.returncode:raise ValueError('APK manifest inspection failed: '+path.name)
    result=parse_package(metadata.stdout)
    result.update({'signers_sha256':parse_verified_signers(verify.stdout),'apk_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'signature_verified':True})
    return result
def compare(old,new):
    same_package=old['package']==new['package']==EXPECTED_PACKAGE
    same_signers=old['signers_sha256']==new['signers_sha256']
    reasons=[]
    if not same_package:reasons.append('package_mismatch')
    if not same_signers:reasons.append('certificate_mismatch')
    if new['version_code']<=old['version_code']:reasons.append('release_version_code_not_increased')
    return {'baseline':old,'candidate':new,'same_package':same_package,'same_signers':same_signers,
        'release_precheck_passed':not reasons,'block_reasons':reasons,
        'scope':'Two CI preview APKs; not the actual installed Samsung app. No signing-lineage compatibility inferred.',
        'device_update_test':'not_run','samsung_install_ready':False}
def main():
    parser=argparse.ArgumentParser();parser.add_argument('baseline');parser.add_argument('candidate');parser.add_argument('--output',required=True);args=parser.parse_args()
    result=compare(inspect_apk(args.baseline),inspect_apk(args.candidate))
    Path(args.output).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
