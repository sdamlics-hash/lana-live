"""Extend the existing V3.6 APK with a real local workspace, preserving voice guards."""
import importlib.util
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', A[1:-1])
def make_preview(archive, destination):
    spec = importlib.util.spec_from_file_location('lana_v36_base', ROOT/'android_voice/patch_v36.py')
    base = importlib.util.module_from_spec(spec); spec.loader.exec_module(base)
    path = base.make_preview(archive, Path(destination))
    main = path/'app/src/main'
    java = main/'java/ai/dreibus/lanalive'
    activity = java/'MainActivity.kt'
    code = activity.read_text()
    anchor = '        setContentView(R.layout.activity_main)'
    if code.count(anchor) != 1: raise ValueError('Unexpected activity layout anchor')
    activity.write_text(code.replace(anchor, anchor+'\n        LanaWorkspace(this).mount()', 1))
    shutil.copyfile(ROOT/'android_workspace/src/LanaWorkspace.kt', java/'LanaWorkspace.kt')
    layout = main/'res/layout/activity_main.xml'
    root = ET.fromstring(layout.read_text()); column = root[0]
    children = list(column)
    if len(children) != 15: raise ValueError('Unexpected V3.6 controls; refusing to drop controls')
    settings = ET.Element('LinearLayout', {A+'id':'@+id/workspaceSettings',A+'layout_width':'match_parent', A+'layout_height':'wrap_content', A+'orientation':'vertical',A+'padding':'18dp'})
    for node in children[7:]: column.remove(node); settings.append(node)
    column.append(settings)
    replacements = {'#0B1410':'#FAF4EC','#193527':'#F0E4D8','#E7FBEA':'#342C2A','#B9F6CA':'#796258','#D1E8D6':'#796258','#67FF87':'#238735','#FFFFFF':'#342C2A','#A1B5A7':'#796258','#829D8A':'#796258','#255D3B':'#E9D3C5','#46D86A':'#D56850','#07130B':'#FFFFFF'}
    for element in root.iter():
        for key, value in list(element.attrib.items()):
            if value in replacements: element.set(key, replacements[value])
        if element.tag == 'EditText':
            element.set(A+'textColor','#342C2A');element.set(A+'textColorHint','#796258')
    children[0].set(A+'text','DREIBUS AI · ЛАНА')
    children[2].set(A+'text','Твой голос. Твои решения. Твоя команда.')
    children[10].set(A+'text','LANA Live V3.7 · локальные задачи и память')
    layout.write_text(ET.tostring(root,encoding='unicode'))
    styles = main/'res/values/styles.xml'
    tree = ET.fromstring(styles.read_text())
    for item in tree.iter('item'):
        name=item.get('name')
        if name in ('android:statusBarColor','android:navigationBarColor'):item.text='#FAF4EC'
        if name=='android:windowLightStatusBar':item.text='true'
        if name=='android:colorAccent':item.text='#C15743'
    theme=tree.find('style')
    ET.SubElement(theme,'item',{'name':'android:windowLightNavigationBar'}).text='true'
    styles.write_text(ET.tostring(tree,encoding='unicode'))
    build=path/'app/build.gradle.kts';s=build.read_text()
    if s.count('0.5.2-lana-voice-reliable')!=1:raise ValueError('Unexpected version')
    build.write_text(s.replace('0.5.2-lana-voice-reliable','0.5.3-lana-workspace'))
    return path
if __name__=='__main__':
    make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
    print('LANA V3.7 workspace source prepared; installed application unchanged')
