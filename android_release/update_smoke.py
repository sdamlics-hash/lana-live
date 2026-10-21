"""Real emulator update attempt, preserving the baseline app and its records."""
import json
from pathlib import Path
import runpy
import subprocess
import sys
report_path=Path(sys.argv[1]);report=json.loads(report_path.read_text())
assert report['same_package'],'Different package is not an in-place update test'
old=Path('/tmp/lana-release/baseline/app-debug.apk')
new=Path('/tmp/lana-release/candidate/app-debug.apk')
expected=Path('/tmp/lana-v39/app/build/outputs/apk/debug/app-debug.apk')
expected.parent.mkdir(parents=True,exist_ok=True);expected.write_bytes(old.read_bytes())
# Uses only the test APK and controlled test records in a fresh emulator.
ctx=runpy.run_path('.github/scripts/lana-v39-connections-smoke.py')
attempt=subprocess.run(['adb','install','-r',str(new)],capture_output=True,text=True,timeout=90)
output=attempt.stdout+attempt.stderr
if report['same_signers']:
    assert attempt.returncode==0,'Matching-signer update failed: '+output
    report['device_update_test']='compatible_update_preserved_records'
else:
    assert attempt.returncode!=0 and 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' in output,'Expected explicit incompatible-update rejection: '+output
    report['device_update_test']='signature_mismatch_rejected_without_uninstall'
adb=ctx['adb'];tap=ctx['tap'];find=ctx['find'];package=ctx['PACKAGE']
adb('shell','am','force-stop',package)
adb('shell','am','start','-W','-n',package+'/ai.dreibus.lanalive.MainActivity')
tap(desc='workspace_tab_1');find(text='✓ LANA_TASK_37')
tap(desc='workspace_tab_2');find(text='LANA_NOTE_37')
report['baseline_records_preserved']=True
report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('RELEASE UPDATE RESULT:',report['device_update_test'],'; task/note preserved; Samsung install readiness remains UNVERIFIED')
