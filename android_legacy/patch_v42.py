"""Observed legacy server contract, explicit mode, bounded RAM history and local attempt cap."""
import importlib.util
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'
def exact(s,old,new,label):
    if s.count(old)!=1:raise ValueError('Unexpected V4.1 anchor: '+label)
    return s.replace(old,new,1)
def make_preview(archive,destination):
    spec=importlib.util.spec_from_file_location('v41',ROOT/'android_release/patch_v41.py')
    base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
    out=base.make_preview(archive,Path(destination));src=out/'app/src/main/java/ai/dreibus/lanalive'
    for name in ('LanaChatClient.kt','LanaChatProtocol.kt','LanaLegacyQuota.kt'):
        shutil.copyfile(ROOT/'android_legacy/src'/name,src/name)
    activity=src/'MainActivity.kt';s=activity.read_text()
    s=exact(s,'    private var conversationActive = false','    private val legacyDialogHistory = LanaLegacyHistory()\n    private var conversationActive = false','history')
    anchor='        status = findViewById(R.id.statusText)'
    s=exact(s,anchor,anchor+'''
        val legacyPrefs = getSharedPreferences("lana_connection_mode", MODE_PRIVATE)
        val legacyChoice = findViewById<android.widget.CheckBox>(R.id.legacyServerMode)
        legacyChoice.isChecked = legacyPrefs.getBoolean("legacy", false)
        var revertingLegacyChoice = false
        legacyChoice.setOnCheckedChangeListener { button, checked ->
            if (!revertingLegacyChoice && !legacyPrefs.edit().putBoolean("legacy", checked).commit()) {
                revertingLegacyChoice = true
                button.isChecked = !checked
                revertingLegacyChoice = false
                status.text = "Не удалось сохранить выбор сервера. Прежний режим оставлен."
            }
        }''','explicit mode')
    anchor='        val chosenNotes = LanaWorkspace.contextNotes()'
    s=exact(s,anchor,anchor+'''
        val legacyMode = findViewById<android.widget.CheckBox>(R.id.legacyServerMode).isChecked
        if (legacyMode) {
            val quotaError = LanaLegacyQuota.claim(this)
            if (quotaError != null) { speakFromLana(quotaError); return }
        }
        val tokenScope = java.util.Base64.getEncoder().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)))
        val historyScope = address + "|" + voiceConversationId + "|" + legacyMode + "|" + tokenScope
        val priorTurns = legacyDialogHistory.snapshot(historyScope)
''','scope and quota')
    s=exact(s,'        Thread {\n            val reply = try {','        Thread {\n            var serverSucceeded = false\n            val reply = try {','result flag')
    s=exact(s,'LanaChatClient.ask(address, secret, voiceConversationId, question, chosenNotes)',
        'LanaChatClient.ask(address, secret, voiceConversationId, question, chosenNotes, legacyMode, priorTurns).also { serverSucceeded = true }','protocol')
    anchor='                if (!voiceSession.accepts(requestTicket)) return@runOnUiThread'
    s=exact(s,anchor,anchor+'''
                if (legacyMode && serverSucceeded) legacyDialogHistory.commit(historyScope, question, reply)''','accepted replies only')
    activity.write_text(s)
    layout=out/'app/src/main/res/layout/activity_main.xml';tree=ET.fromstring(layout.read_text())
    settings=next(n for n in tree.iter() if n.get(A+'id')=='@+id/workspaceSettings')
    settings.insert(0,ET.Element('CheckBox',{A+'id':'@+id/legacyServerMode',A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'text':'Использовать существующий сервер Ланы',A+'contentDescription':'legacy_server_mode',A+'textColor':'#342C2A',A+'saveEnabled':'false'}))
    settings.insert(1,ET.Element('TextView',{A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'text':'В этом режиме текущий диалог передаётся существующему серверу. История ChatGPT не подключается. Лимит приложения: 30 попыток в час, 100 за 24 часа.',A+'textColor':'#796258'}))
    layout.write_text(ET.tostring(tree,encoding='unicode').replace('LANA Live V4.1','LANA Live V4.2'))
    testdir=out/'app/src/test/java/ai/dreibus/lanalive'
    shutil.copyfile(ROOT/'android_legacy/tests/LanaLegacyProtocolTest.kt',testdir/'LanaLegacyProtocolTest.kt')
    build=out/'app/build.gradle.kts';s=build.read_text()
    s=exact(s,'versionCode = 6','versionCode = 7','versionCode')
    s=exact(s,'0.6.1-lana-update-prep','0.6.2-lana-legacy-contract','versionName')
    s=exact(s,'dependencies { testImplementation("junit:junit:4.13.2") }','dependencies {\n    testImplementation("junit:junit:4.13.2")\n    testImplementation("org.json:json:20240303")\n}','real JSON JVM tests')
    build.write_text(s)
    return out
if __name__=='__main__':make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
