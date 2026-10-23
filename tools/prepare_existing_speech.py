"""Review/apply a speech-router addition to an existing FastAPI file; never restart it."""
import argparse
import ast
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import tempfile

def planned_source(source):
    tree=ast.parse(source)
    if 'lana_speech_router' in source:raise ValueError('Speech router already mentioned; inspect rather than duplicate it')
    apps=[]
    for node in tree.body:
        if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name):
            call=node.value
            if isinstance(call,ast.Call) and ((isinstance(call.func,ast.Name) and call.func.id=='FastAPI') or
                    (isinstance(call.func,ast.Attribute) and call.func.attr=='FastAPI')):
                apps.append((node.targets[0].id,node.end_lineno))
    if len(apps)!=1:raise ValueError('Expected exactly one top-level FastAPI app; no change made')
    for node in ast.walk(tree):
        if isinstance(node,ast.Constant) and node.value in ('/v1/lana/speech','/v1/lana/speech/status'):
            raise ValueError('Speech route already exists; no change made')
    name,line=apps[0]
    lines=source.splitlines(keepends=True)
    addition='\nfrom lana_speech_router import create_router as _lana_speech_router\n'+name+'.include_router(_lana_speech_router())\n'
    lines.insert(line,addition)
    result=''.join(lines);compile(result,'lana_api.py','exec')
    return result,name

def prepare(backend,module,apply=False):
    backend=Path(backend);module=Path(module)
    if backend.is_symlink() or module.is_symlink():raise ValueError('Symlink inputs refused')
    source=backend.read_text();updated,name=planned_source(source)
    router=backend.with_name('lana_speech_router.py')
    if router.exists():raise ValueError('Router target already exists; inspect it first')
    compile(module.read_text(),str(router),'exec')
    if not apply:return {'mode':'plan','app':name,'backend':str(backend),'router':str(router),'restart_performed':False}
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup=backend.with_name(backend.name+'.before-speech-'+stamp)
    fd=os.open(backup,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:f.write(source)
    fd=os.open(router,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'wb') as f:f.write(module.read_bytes())
    try:
        with tempfile.NamedTemporaryFile('w',dir=backend.parent,delete=False) as f:
            tmp=Path(f.name);f.write(updated)
        os.chmod(tmp,backend.stat().st_mode&0o777)
        os.replace(tmp,backend)
    except BaseException:
        router.unlink(missing_ok=True)
        if 'tmp' in locals():tmp.unlink(missing_ok=True)
        raise
    return {'mode':'applied','backup':str(backup),'backend':str(backend),'router':str(router),'restart_performed':False}

if __name__=='__main__':
    import json
    p=argparse.ArgumentParser();p.add_argument('--backend',type=Path,default=Path.home()/'lana_backend/project/backend/lana_api.py')
    p.add_argument('--speech-module',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args()
    try:print(json.dumps(prepare(a.backend,a.speech_module,a.apply),ensure_ascii=False,indent=2))
    except (ValueError,OSError,SyntaxError) as e:raise SystemExit(type(e).__name__+': no server restart performed; inspect input files') from None
