from pathlib import Path


def install(out: Path, root: Path):
    src = out / 'app/src/main/java/ai/dreibus/lanalive'
    tests = out / 'app/src/test/java/ai/dreibus/lanalive'
    for name in ('LanaDialogueStyle.kt', 'LanaDialogueSettings.kt'):
        (src / name).write_text((root / 'android_team/src' / name).read_text())
    (tests / 'LanaDialogueStyleTest.kt').write_text((root / 'android_team/tests/LanaDialogueStyleTest.kt').read_text())
    for filename, old, new in (
        ('MainActivity.kt', 'LanaDialoguePrompt.build(question)', 'LanaDialogueSettings.prompt(this,question)'),
        ('LanaVoiceKeepAliveService.kt', 'LanaDialoguePrompt.build(text)', 'LanaDialogueSettings.prompt(this@LanaVoiceKeepAliveService,text)'),
        ('LanaWorkspace.kt', '        addPanel(settings)', '        LanaDialogueSettings.mount(activity,settings)\n        addPanel(settings)'),
    ):
        target = src / filename
        text = target.read_text()
        if text.count(old) != 1:
            raise ValueError('Dialogue style anchor not unique: ' + filename)
        target.write_text(text.replace(old, new))
