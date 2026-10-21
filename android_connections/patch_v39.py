"""V3.9 connection evidence and explicit legacy route, preserving V3.8 consent."""
import importlib.util
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'
def make_preview(archive,destination):
    spec=importlib.util.spec_from_file_location('v38',ROOT/'android_memory/patch_v38.py')
    base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
    out=base.make_preview(archive,Path(destination))
    folder=out/'app/src/main/java/ai/dreibus/lanalive'
    for name in ('LanaCoreClient.kt','LanaConnectionStatus.kt'):
        shutil.copyfile(ROOT/'android_connections/src'/name,folder/name)
    client=folder/'LanaChatClient.kt';s=client.read_text()
    old='URL(base.toExternalForm().trimEnd(\'/\') + "/v1/chat")'
    if s.count(old)!=1:raise ValueError('Missing single-request chat URL')
    client.write_text(s.replace(old,'URL(LanaConnectionStatus.chatUrl(baseUrl))'))
    activity=folder/'MainActivity.kt';s=activity.read_text()
    anchor='runOnUiThread { if (!isFinishing && !isDestroyed) status.text = answer }'
    if s.count(anchor)!=1:raise ValueError('Missing diagnostics callback')
    s=s.replace('"Связь не установлена. Проверьте HTTPS-адрес и доступ к серверу."',
        '"Связь не установлена.\\n" + LanaConnectionStatus.render("unknown", "unknown", "unknown")')
    s=s.replace(anchor,'''runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        status.text = answer
                        findViewById<TextView>(R.id.connectionStatusText).text = answer
                    }
                }''')
    activity.write_text(s)
    layout=out/'app/src/main/res/layout/activity_main.xml'
    tree=ET.fromstring(layout.read_text());settings=next(n for n in tree.iter() if n.get(A+'id')=='@+id/workspaceSettings')
    initial='OpenAI API — не проверен\nИстория ChatGPT — не подключена\nСерверная память Ланы — не проверена\nНажми «Проверить связь с ядром». Проверка не вызывает модель.'
    settings.insert(0,ET.Element('TextView',{A+'id':'@+id/connectionStatusText',A+'contentDescription':'connection_status',A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'text':initial,A+'textColor':'#342C2A',A+'padding':'8dp'}))
    layout.write_text(ET.tostring(tree,encoding='unicode').replace('LANA Live V3.8','LANA Live V3.9'))
    build=out/'app/build.gradle.kts';build.write_text(build.read_text().replace('0.5.4-lana-memory-consent','0.5.5-lana-connections'))
    tests=out/'app/src/test/java/ai/dreibus/lanalive'
    shutil.copyfile(ROOT/'android_connections/tests/LanaConnectionStatusTest.kt',tests/'LanaConnectionStatusTest.kt')
    return out
if __name__=='__main__':
    make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
