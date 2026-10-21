"""Print API structure only. Never import/execute backend or print config/values."""
import ast
import json
from pathlib import Path
import sys

def inspect_source(source):
    tree=ast.parse(source)
    models={}
    routes=[]
    for node in tree.body:
        if isinstance(node,ast.ClassDef) and any(
            isinstance(base,ast.Name) and base.id=='BaseModel' or
            isinstance(base,ast.Attribute) and base.attr=='BaseModel'
            for base in node.bases):
            models[node.name]=[item.target.id for item in node.body
                if isinstance(item,ast.AnnAssign) and isinstance(item.target,ast.Name)]
        if not isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef)):
            continue
        for decorator in node.decorator_list:
            if not (isinstance(decorator,ast.Call) and isinstance(decorator.func,ast.Attribute)
                    and decorator.func.attr in ('get','post','put','patch','delete')
                    and decorator.args and isinstance(decorator.args[0],ast.Constant)
                    and isinstance(decorator.args[0].value,str)):
                continue
            path=decorator.args[0].value
            if path not in ('/v1/lana/chat','/v1/chat','/v1/status','/v1/connections','/healthz'):
                continue
            annotations={arg.arg:arg.annotation.id for arg in node.args.args
                if isinstance(arg.annotation,ast.Name)}
            # Keys only; no dictionary values, prompts, function bodies or credentials.
            return_keys=set()
            for item in ast.walk(node):
                if isinstance(item,ast.Return) and isinstance(item.value,ast.Dict):
                    return_keys.update(key.value for key in item.value.keys
                        if isinstance(key,ast.Constant) and isinstance(key.value,str))
            routes.append({'method':decorator.func.attr.upper(),'path':path,
                'input_types':annotations,'literal_response_keys':sorted(return_keys)})
    # Only models referenced by an actual route are reported.
    used={name for route in routes for name in route['input_types'].values()}
    return {'routes':routes,'models':{k:v for k,v in models.items() if k in used},
            'note':'Structure only; computed schemas and response values are deliberately omitted.'}

if __name__=='__main__':
    path=Path(sys.argv[1]).expanduser() if len(sys.argv)>1 else Path.home()/'lana_backend/project/backend/lana_api.py'
    try:
        result=inspect_source(path.read_text())
    except (OSError,SyntaxError,UnicodeError):
        raise SystemExit('Cannot read/parse backend source; no file contents or secrets printed.') from None
    print(json.dumps(result,ensure_ascii=False,indent=2))
