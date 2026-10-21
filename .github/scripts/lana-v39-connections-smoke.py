"""Real emulator taps: create local task/note, restart, preserve records, cancel deletion."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
PACKAGE='ai.dreibus.lanalive.voicepreview'
def adb(*args):
    return subprocess.check_output(['adb',*args],text=True,timeout=30)
def snapshot():
    adb('shell','uiautomator','dump','/sdcard/lana-v37.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/lana-v37.xml'))
def find(text=None,desc=None):
    for _ in range(8):
        try:
            root = snapshot()
            launcher_anr = any(node.get('resource-id') == 'android:id/alertTitle' and
                               node.get('text') == "Pixel Launcher isn't responding"
                               for node in root.iter('node'))
            if launcher_anr:
                close = next(node for node in root.iter('node') if node.get('resource-id') == 'android:id/aerr_close')
                bounds = list(map(int,re.findall(r'\d+',close.get('bounds'))))
                adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
                adb('shell','am','start','-W','-n',PACKAGE+'/ai.dreibus.lanalive.MainActivity')
                time.sleep(1)
                continue
            nodes = root.iter('node')
            for node in nodes:
                if (text is not None and node.get('text','').casefold()==text.casefold()) or (desc is not None and node.get('content-desc')==desc):return node
        except (subprocess.CalledProcessError, ET.ParseError):
            pass  # Android UI tree may not exist immediately after launch.
        time.sleep(1)
    try:
        print(ET.tostring(snapshot(),encoding='unicode'))
    except Exception:
        pass
    print(adb('logcat','-d','-s','AndroidRuntime:E'))
    raise AssertionError('Control missing: '+str(text or desc))
def tap(text=None,desc=None):
    node=find(text,desc);nums=list(map(int,re.findall(r'\d+',node.get('bounds'))))
    adb('shell','input','tap',str((nums[0]+nums[2])//2),str((nums[1]+nums[3])//2))
def add(tab,field,value,button):
    tap(desc='workspace_tab_'+str(tab));tap(desc=field)
    adb('shell','input','text',value);adb('shell','input','keyevent','4');tap(text=button);find(text=value)
adb('shell','input','keyevent','82')
adb('install','-r','/tmp/lana-v39/app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','start','-W','-n',PACKAGE+'/ai.dreibus.lanalive.MainActivity')
find(desc='workspace_tab_0')
add(1,'workspace_task_input','LANA_TASK_37','Добавить задачу')
add(2,'workspace_note_input','LANA_NOTE_37','Сохранить заметку')
tap(desc='memory_consent_0')
assert find(desc='memory_consent_0').get('checked')=='true', 'Chosen note must be visibly selected'
adb('shell','am','force-stop',PACKAGE)
adb('shell','am','start','-W','-n',PACKAGE+'/ai.dreibus.lanalive.MainActivity')
tap(desc='workspace_tab_1');find(text='LANA_TASK_37')
tap(text='Отметить выполненной');find(text='✓ LANA_TASK_37')
tap(text='Удалить');tap(text='Отмена');find(text='✓ LANA_TASK_37')
tap(desc='workspace_tab_2');find(text='LANA_NOTE_37')
assert find(desc='memory_consent_0').get('checked')=='false', 'Consent must not silently survive process restart'
tap(desc='workspace_tab_4');find(text='ПРОВЕРИТЬ СВЯЗЬ С ЯДРОМ')
status = find(desc='connection_status')
assert 'OpenAI API — не проверен' in status.get('text', '')
assert 'История ChatGPT — не подключена' in status.get('text', '')
assert 'Серверная память Ланы — не проверена' in status.get('text', '')
tap(desc='workspace_tab_0');find(text='НАША ПЕРЕПИСКА')
print('V3.9 emulator: truthful initial connection labels; explicit note selection and reset after restart;  task/note saved across restart, completion, deletion cancel, settings/voice navigation passed')
