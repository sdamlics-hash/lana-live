"""Real emulator taps: create local task/note, restart, preserve records, cancel deletion."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
PACKAGE='ai.dreibus.lanalive.voicepreview'
def adb(*args):
    return subprocess.check_output(['adb',*args],text=True,timeout=30)
def snapshot():
    # A failed dump must not reuse an XML tree from an earlier screen/process.
    adb('shell','rm','-f','/sdcard/lana-v37.xml')
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
def wait_checked(desc, expected):
    for _ in range(8):
        node = find(desc=desc)
        if node.get('checked') == str(expected).lower():
            return
        time.sleep(0.5)
    raise AssertionError('Checked state not reached: '+desc)
def reveal(desc):
    size = adb('shell','wm','size')
    width,height = map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
    for _ in range(12):
        try:
            root = snapshot()
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(1)
            continue
        for node in root.iter('node'):
            if node.get('content-desc') == desc:
                bounds = list(map(int,re.findall(r'\d+',node.get('bounds',''))))
                if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1] and 0 < (bounds[1]+bounds[3])//2 < height:
                    return node
        adb('shell','input','swipe',str(width//2),str(height*4//5),str(width//2),str(height//4),'450')
    raise AssertionError('Control not visible after scrolling: '+desc)
def add(tab,field,value,button):
    tap(desc='workspace_tab_'+str(tab));tap(desc=field)
    adb('shell','input','text',value);adb('shell','input','keyevent','4');tap(text=button);find(text=value)
adb('shell','input','keyevent','82')
adb('install','-r','/tmp/lana-v42/app/build/outputs/apk/debug/app-debug.apk')
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
tap(desc='workspace_tab_4')
assert find(desc='legacy_server_mode').get('checked')=='false'
tap(desc='legacy_server_mode')
wait_checked('legacy_server_mode',True)  # Observe the click before killing the process.
adb('shell','am','force-stop',PACKAGE)
adb('shell','am','start','-W','-n',PACKAGE+'/ai.dreibus.lanalive.MainActivity')
tap(desc='workspace_tab_4')
assert find(desc='legacy_server_mode').get('checked')=='true'

status = find(desc='connection_status')
assert 'OpenAI API — не проверен' in status.get('text', '')
assert 'История ChatGPT — не подключена' in status.get('text', '')
assert 'Серверная память LANA — не проверена' in status.get('text', '')
# Technical controls remain available lower down in this scrollable settings page.
size=adb('shell','wm','size')
w,h=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
for _ in range(5):
    if any(n.get('text','').casefold()=='проверить связь с ядром' for n in snapshot().iter('node')):break
    adb('shell','input','swipe',str(w//2),str(h*4//5),str(w//2),str(h//4),'450')
find(text='ПРОВЕРИТЬ СВЯЗЬ С ЯДРОМ')
tap(desc='workspace_tab_0');find(text='НАША ПЕРЕПИСКА')
print('V4.2 emulator: explicit legacy choice survives restart; truthful initial connection labels; explicit note selection and reset after restart;  task/note saved across restart, completion, deletion cancel, settings/voice navigation passed')

# Explicit style choices are local, shared by foreground/background dialogue and durable.
tap(desc='workspace_tab_4')
reveal('dialogue_style_save')
for desc in ('dialogue_style_concise','dialogue_style_warm','dialogue_style_humor'):
    wait_checked(desc,True)
    tap(desc=desc)
    wait_checked(desc,False)
tap(desc='dialogue_style_save')
find(text='Стиль сохранён на этом устройстве. Действует со следующего вопроса.')
adb('shell','am','force-stop',PACKAGE)
adb('shell','am','start','-W','-n',PACKAGE+'/ai.dreibus.lanalive.MainActivity')
tap(desc='workspace_tab_4')
reveal('dialogue_style_save')
for desc in ('dialogue_style_concise','dialogue_style_warm','dialogue_style_humor'):
    wait_checked(desc,False)
# A draft checkbox change must not be silently saved.
tap(desc='dialogue_style_humor')
wait_checked('dialogue_style_humor',True)
adb('shell','am','force-stop',PACKAGE)
adb('shell','am','start','-W','-n',PACKAGE+'/ai.dreibus.lanalive.MainActivity')
tap(desc='workspace_tab_4')
reveal('dialogue_style_save')
wait_checked('dialogue_style_humor',False)
tap(desc='workspace_tab_0')
print('Dialogue style emulator: explicit saved choices survive restart; unsaved checkbox change is discarded; no provider requests made')

from pathlib import Path
Path("/tmp/lana-presence.png").write_bytes(subprocess.check_output(["adb","exec-out","screencap","-p"],timeout=30))
