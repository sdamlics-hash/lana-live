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
    for _ in range(5):
        for node in snapshot().iter('node'):
            if (text is not None and node.get('text','').casefold()==text.casefold()) or (desc is not None and node.get('content-desc')==desc):return node
        time.sleep(.4)
    raise AssertionError('Control missing: '+str(text or desc))
def tap(text=None,desc=None):
    node=find(text,desc);nums=list(map(int,re.findall(r'\d+',node.get('bounds'))))
    adb('shell','input','tap',str((nums[0]+nums[2])//2),str((nums[1]+nums[3])//2))
def add(tab,field,value,button):
    tap(desc='workspace_tab_'+str(tab));tap(desc=field)
    adb('shell','input','text',value);adb('shell','input','keyevent','4');tap(text=button);find(text=value)
adb('install','-r','/tmp/lana-v37/app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','start','-n',PACKAGE+'/ai.dreibus.lanalive.MainActivity')
find(desc='workspace_tab_0')
add(1,'workspace_task_input','LANA_TASK_37','Добавить задачу')
add(2,'workspace_note_input','LANA_NOTE_37','Сохранить заметку')
adb('shell','am','force-stop',PACKAGE)
adb('shell','am','start','-n',PACKAGE+'/ai.dreibus.lanalive.MainActivity')
tap(desc='workspace_tab_1');find(text='LANA_TASK_37')
tap(text='Отметить выполненной');find(text='✓ LANA_TASK_37')
tap(text='Удалить');tap(text='Отмена');find(text='✓ LANA_TASK_37')
tap(desc='workspace_tab_2');find(text='LANA_NOTE_37')
tap(desc='workspace_tab_4');find(text='ПРОВЕРИТЬ СВЯЗЬ С ЯДРОМ')
tap(desc='workspace_tab_0');find(text='НАША ПЕРЕПИСКА')
print('V3.7 emulator: task/note saved across restart, completion, deletion cancel, settings/voice navigation passed')
