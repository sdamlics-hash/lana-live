"""V3.8: explicit ephemeral selection of local notes for chat; no new provider calls."""
import importlib.util
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def make_preview(archive,destination):
    spec=importlib.util.spec_from_file_location('workspace',ROOT/'android_workspace/patch_v37.py')
    base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
    out=base.make_preview(archive,Path(destination))
    folder=out/'app/src/main/java/ai/dreibus/lanalive'
    activity=folder/'MainActivity.kt';s=activity.read_text()
    old='        val requestTicket = voiceSession.current()'
    if s.count(old)!=1:raise ValueError('Missing guarded request anchor')
    s=s.replace(old,old+'\n        val chosenNotes = LanaWorkspace.contextNotes()',1)
    s=s.replace('LanaChatClient.ask(address, secret, voiceConversationId, question)',
        'LanaChatClient.ask(address, secret, voiceConversationId, question, chosenNotes)')
    activity.write_text(s)
    workspace=folder/'LanaWorkspace.kt';s=workspace.read_text()
    anchor='    private val ink = Color.rgb(52, 44, 42)'
    if s.count(anchor)!=1:raise ValueError('Missing workspace anchor')
    s=s.replace(anchor,'''    companion object {
        private val selectedNotes = linkedSetOf<String>()
        fun contextNotes(): List<String> = selectedNotes.toList()
    }
'''+anchor,1)
    s=s.replace('            readable = false //', '            selectedNotes.clear()\n            readable = false //')
    s=s.replace('Подтверждённые тобой заметки на этом планшете. Они пока не передаются серверному ИИ автоматически. История ChatGPT не подключена.',
        'Выбери заметки для ответов Ланы: они будут переданы серверу и OpenAI со следующим вопросом. Выбор временный, до остановки приложения. Ранее отправленные сведения могут остаться в истории разговора. История ChatGPT не подключена.')
    anchor='            row.addView(button("Удалить") {'
    if s.count(anchor)!=1:raise ValueError('Missing record action anchor')
    s=s.replace(anchor,'''            if (!taskMode) {
                val note = records.getString(i)
                val choice = CheckBox(activity).apply {
                    text = "Использовать в ответах ИИ"
                    setTextColor(ink)
                    isChecked = selectedNotes.contains(note)
                    contentDescription = "memory_consent_$i"
                    setOnCheckedChangeListener { control, checked ->
                        if (checked) {
                            val next = selectedNotes + note
                            if (next.size > 5 || next.sumOf { it.length } > 6000) {
                                control.isChecked = false
                                notice("Можно выбрать до 5 заметок и 6000 символов. Сними лишние галочки.")
                            } else selectedNotes.add(note)
                        } else selectedNotes.remove(note)
                    }
                }
                row.addView(choice)
            }
'''+anchor,1)
    old='if (taskMode) notes else without(notes, i))) renderRecords(taskMode)'
    if s.count(old)!=1:raise ValueError('Missing delete confirmation anchor')
    s=s.replace(old,'''if (taskMode) notes else without(notes, i))) {
                        if (!taskMode) selectedNotes.remove(records.getString(i))
                        renderRecords(taskMode)
                    }''',1)
    workspace.write_text(s)
    layout=out/'app/src/main/res/layout/activity_main.xml'
    layout.write_text(layout.read_text().replace('LANA Live V3.7','LANA Live V3.8'))
    build=out/'app/build.gradle.kts';s=build.read_text().replace('0.5.3-lana-workspace','0.5.4-lana-memory-consent');build.write_text(s)
    return out
if __name__=='__main__':
    make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
    print('LANA V3.8 memory consent prepared; no secrets or installed apps modified')
