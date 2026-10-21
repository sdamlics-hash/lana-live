"""Read-only LANA HTTPS acceptance probe. No paid model calls or state writes."""
import argparse
import http.client
import json
import os
import ssl
import urllib.error
import urllib.parse
import urllib.request

class ConnectionCheckError(RuntimeError):
    pass

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def check_connection(base_url, token, opener=None):
    try:
        parsed = urllib.parse.urlsplit(base_url)
        port = parsed.port
    except (ValueError, TypeError):
        raise ConnectionCheckError('Неверный HTTPS-адрес') from None
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username is not None
            or parsed.password is not None or parsed.query or parsed.fragment
            or any(c.isspace() for c in base_url)):
        raise ConnectionCheckError('Нужен HTTPS-адрес без логина, параметров и фрагмента')
    if not isinstance(token, str) or len(token) < 32 or any(ord(c) < 33 or ord(c) > 126 for c in token):
        raise ConnectionCheckError('Нет действующего ключа Ланы (не ключа OpenAI)')
    client = opener or urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=ssl.create_default_context()), NoRedirect())
    endpoint = base_url.rstrip('/') + '/v1/status'
    def request(authorized):
        headers={'Accept':'application/json'}
        if authorized:
            headers['Authorization']='Bearer '+token
        req=urllib.request.Request(endpoint, headers=headers)
        try:
            response=client.open(req, timeout=8)
        except urllib.error.HTTPError as exc:
            code=exc.code
            exc.close()
            return code, None
        except (OSError, http.client.HTTPException, urllib.error.URLError, ValueError):
            # Never echo exception text: it can contain URLs or credentials.
            raise ConnectionCheckError('Нет подтверждённого HTTPS-соединения: проверь адрес, сеть и сертификат') from None
        with response:
            if response.geturl()!=endpoint:
                raise ConnectionCheckError('Перенаправление запрещено')
            if response.status!=200:
                return response.status,None
            if response.headers.get_content_type()!='application/json':
                raise ConnectionCheckError('Сервер вернул не JSON Ланы')
            raw=response.read(8193)
            if len(raw)>8192:
                raise ConnectionCheckError('Слишком большой ответ сервера')
            try:
                return response.status,json.loads(raw)
            except (ValueError,UnicodeError):
                raise ConnectionCheckError('Неверный ответ сервера') from None
    code,_=request(False)
    if code!=401:
        raise ConnectionCheckError('Проверка защиты не пройдена: без ключа ожидается HTTP 401')
    code,data=request(True)
    if code in (401,403):
        raise ConnectionCheckError('Сервер не принял ключ Ланы')
    if (code!=200 or not isinstance(data,dict) or data.get('ok') is not True
            or data.get('mode')!='offline-prototype'
            or not isinstance(data.get('allowed'),list)
            or not all(isinstance(item,str) for item in data['allowed'])
            or set(data['allowed'])!={'heartbeat','trade_report'}):
        raise ConnectionCheckError('Подлинность интерфейса Ланы не подтверждена')
    return {'https_verified':True,'authentication_verified':True,
            'chatgpt_history_connected':False,'live_model_tested':False,
            'microphone_tested':False,'memory_persistence_tested':False,
            'paid_api_calls':0}

def main():
    parser=argparse.ArgumentParser(description='Проверка HTTPS и авторизации Ланы без запросов к ИИ')
    parser.add_argument('--url',required=True)
    args=parser.parse_args()
    try:
        result=check_connection(args.url,os.environ.get('LANA_API_TOKEN',''))
    except ConnectionCheckError as exc:
        print(json.dumps({'ok':False,'error':str(exc)},ensure_ascii=False))
        return 1
    print(json.dumps({'ok':True,**result},ensure_ascii=False))
    return 0
if __name__=='__main__':
    raise SystemExit(main())
