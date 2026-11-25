"""Real native CPU synthesis, with network namespace disabled by CI."""
import json, math, pathlib, sys, time, wave
import numpy as np
import sherpa_onnx

model=pathlib.Path(sys.argv[1]).resolve()
out=pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=str(model/'ru_RU-irina-medium.onnx'),tokens=str(model/'tokens.txt'),data_dir=str(model/'espeak-ng-data'))
cfg=sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=vits,num_threads=2,debug=False,provider="cpu"),max_num_sentences=1)
assert cfg.validate()
engine=sherpa_onnx.OfflineTts(cfg)
phrases=[
"Привет! Я Лана. Я тебя слышу.",
"Сегодня среда, двадцать пятое ноября.",
"Я работаю без интернета.",
"Хорошо, я сохранила заметку.",
"Давай обсудим наш план.",
"Что ты хочешь узнать?",
"Я не знаю ответа на этот вопрос.",
"Голос остановлен.",
"Сейчас попробуем ещё раз.",
"Я готова к разговору. Расскажи, как прошёл твой день."
]
report=[]
for i,text in enumerate(phrases,1):
    start=time.monotonic()
    audio=engine.generate(text=text,sid=0,speed=1.0)
    elapsed=time.monotonic()-start
    s=np.asarray(audio.samples,dtype=np.float32)
    rate=int(audio.sample_rate)
    assert rate==22050,(i,rate)
    assert s.ndim==1 and s.size>rate//4 and s.size<rate*40
    assert np.isfinite(s).all()
    rms=float(np.sqrt(np.mean(s*s)))
    peak=float(np.max(np.abs(s)))
    assert 0.003<rms<0.8 and 0.01<peak<1.5,(i,rms,peak)
    duration=len(s)/rate
    wav=out/f"voice-{i:02}.wav"
    with wave.open(str(wav),"wb") as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes((np.clip(s,-1,1)*32767).astype("<i2").tobytes())
    with wave.open(str(wav),"rb") as w:
        assert w.getnframes()==s.size and w.getframerate()==rate
    report.append(dict(case=i,duration_s=round(duration,3),generation_s=round(elapsed,3),real_time_factor=round(elapsed/duration,3),rms=round(rms,5),peak=round(peak,5)))
    print("PASS",report[-1],flush=True)
(out/"report.json").write_text(json.dumps(report,indent=2))
print("10/10 real synthesis checks passed. Audibility/intelligibility and Android playback remain separate gates.")
